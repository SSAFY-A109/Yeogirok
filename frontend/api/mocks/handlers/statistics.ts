import { delay, http } from "msw";

import { VISIT_COUNT_METRIC, type VisitCellDto, type VisitStatisticsResponseDto } from "../../../features/statistics/model";
import { formatServerDateTime } from "../../../lib/date";
import { mockDb } from "../db";
import { categoriesFixture, findCategory } from "../fixtures/common";
import { visitFootprintSeeds, type MockFootprintSeed } from "../fixtures/statistics";
import { fail, MOCK_DELAY_MS, ok } from "./respond";

// API-046 목. backend/dev(fc7b266) VisitStatisticsService·VisitGrid·BoundingBox의 검사와 격자 집계를 따른다.
// 격자 설정은 backend application-local.yaml 값이다. 배포 값은 환경 변수라 다를 수 있다(TBD).
// 예시 묶음(fixtures)과 앱에서 남긴 발자취(mockDb.footprints, API-041 목)를 함께 센다.

const BASE_CELL_DEGREES = 0.005;
/** "최소zoom:배수". zoom이 클수록(가까울수록) 칸이 작다. */
const ZOOM_MULTIPLIERS = [
  { minZoom: 15, multiplier: 1 },
  { minZoom: 13, multiplier: 2 },
  { minZoom: 11, multiplier: 8 },
  { minZoom: 9, multiplier: 32 },
  { minZoom: 0, multiplier: 128 },
];
const MIN_COUNT = 1;
const MAX_CELLS = 2500;
const AGGREGATION_UNIT = "LAT_LNG_GRID";
const AGGREGATION_VERSION = "grid-0.005-v1";
const COORDINATE_DIGITS = 7;
const KOREA = { west: 124, south: 33, east: 132, north: 39 };
const LOCAL_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

type Box = { west: number; south: number; east: number; north: number };
type ParseResult<T> = { value: T } | { error: ReturnType<typeof fail> };

function invalidBbox(reason: string) {
  return { error: fail(400, "C013", `bbox: ${reason}`) };
}

/** 서버 `BoundingBox.parseOrNull`: `west,south,east,north`, 범위 안, west < east·south < north. */
function parseBbox(raw: string | null): ParseResult<Box> {
  if (raw === null) {
    return { error: fail(400, "C001", "bbox는 필수입니다.") };
  }

  const parts = raw.split(",");

  if (parts.length !== 4) {
    return invalidBbox("west,south,east,north 4개 값이 필요합니다.");
  }

  const [west, south, east, north] = parts.map((part) => {
    return part.trim() === "" ? Number.NaN : Number(part);
  });

  if (![west, south, east, north].every(Number.isFinite)) {
    return invalidBbox("숫자가 아닌 값이 있습니다.");
  }

  if (Math.abs(west) > 180 || Math.abs(east) > 180 || Math.abs(south) > 90 || Math.abs(north) > 90) {
    return invalidBbox("위도·경도 범위를 벗어났습니다.");
  }

  if (west >= east || south >= north) {
    return invalidBbox("west < east, south < north 여야 합니다.");
  }

  return { value: { west, south, east, north } };
}

function parseZoom(raw: string | null): ParseResult<number> {
  if (raw === null) {
    return { error: fail(400, "C001", "zoom은 필수입니다.") };
  }

  const zoom = Number(raw);

  if (raw.trim() === "" || Number.isNaN(zoom)) {
    return { error: fail(400, "C002", "zoom은 숫자여야 합니다.") };
  }

  return zoom < 0 ? { error: fail(400, "C003", "zoom: 0 이상이어야 합니다.") } : { value: zoom };
}

function parseDate(raw: string | null, field: string): ParseResult<string | null> {
  if (raw === null) {
    return { value: null };
  }

  return LOCAL_DATE_PATTERN.test(raw) ? { value: raw } : { error: fail(400, "C002", `${field}: 날짜 형식이 아닙니다.`) };
}

function multiplierFor(zoom: number): number {
  return ZOOM_MULTIPLIERS.find((level) => {
    return zoom >= level.minZoom;
  })?.multiplier ?? ZOOM_MULTIPLIERS[ZOOM_MULTIPLIERS.length - 1].multiplier;
}

function intersect(a: Box, b: Box): Box | null {
  const box = {
    west: Math.max(a.west, b.west),
    south: Math.max(a.south, b.south),
    east: Math.min(a.east, b.east),
    north: Math.min(a.north, b.north),
  };

  return box.west < box.east && box.south < box.north ? box : null;
}

/** 걸치는 칸 전체를 덮도록 칸 경계로 넓힌다(서버 `VisitGrid.expandToCells`). */
function expandToCells(box: Box, size: number): Box {
  return {
    west: Math.floor(box.west / size) * size,
    south: Math.floor(box.south / size) * size,
    east: (Math.floor(box.east / size) + 1) * size,
    north: (Math.floor(box.north / size) + 1) * size,
  };
}

function roundCoordinate(value: number): number {
  return Number(value.toFixed(COORDINATE_DIGITS));
}

/** 예시 묶음과 앱에서 남긴 발자취. 앱에서 남긴 발자취는 기간 조건으로 거른다(예시 묶음은 날짜가 없어 늘 센다). */
function countedFootprints(from: string | null, to: string | null): MockFootprintSeed[] {
  const saved = mockDb.footprints.filter((footprint) => {
    const day = footprint.visitedAt.slice(0, 10);
    return (from === null || day >= from) && (to === null || day <= to);
  }).map((footprint) => {
    return { latitude: footprint.latitude, longitude: footprint.longitude, categoryCodes: footprint.categoryCodes, count: 1 };
  });

  return [...visitFootprintSeeds, ...saved];
}

type Tally = { latitudeIndex: number; longitudeIndex: number; count: number; byCategory: Map<string, number> };

function toCells(points: readonly MockFootprintSeed[], area: Box, size: number, multiplier: number, category: string | null): VisitCellDto[] {
  const tallies = new Map<string, Tally>();

  for (const point of points) {
    const isInside = point.latitude >= area.south && point.latitude < area.north && point.longitude >= area.west && point.longitude < area.east;

    if (!isInside || (category !== null && !point.categoryCodes.includes(category))) {
      continue;
    }

    const latitudeIndex = Math.floor(point.latitude / size);
    const longitudeIndex = Math.floor(point.longitude / size);
    const key = `${latitudeIndex}:${longitudeIndex}`;
    const tally = tallies.get(key) ?? { latitudeIndex, longitudeIndex, count: 0, byCategory: new Map<string, number>() };
    tally.count += point.count;

    for (const code of point.categoryCodes) {
      tally.byCategory.set(code, (tally.byCategory.get(code) ?? 0) + point.count);
    }

    tallies.set(key, tally);
  }

  return [...tallies.values()].filter((tally) => {
    return tally.count >= MIN_COUNT;
  }).sort((a, b) => {
    return b.latitudeIndex - a.latitudeIndex || a.longitudeIndex - b.longitudeIndex;
  }).map((tally) => {
    const categoryCounts: Record<string, number> = {};

    for (const { code } of categoriesFixture.categories) {
      const count = tally.byCategory.get(code);

      if (count !== undefined) {
        categoryCounts[code] = count;
      }
    }

    return {
      cellId: `${multiplier}:${tally.latitudeIndex}:${tally.longitudeIndex}`,
      center: {
        latitude: roundCoordinate((tally.latitudeIndex + 0.5) * size),
        longitude: roundCoordinate((tally.longitudeIndex + 0.5) * size),
      },
      count: tally.count,
      categoryCounts,
    };
  });
}

export const statisticsHandlers = [
  http.get("*/api/v1/visit-statistics", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const params = new URL(request.url).searchParams;
    const metric = params.get("metric");
    const category = params.get("category");

    if (metric !== null && metric !== VISIT_COUNT_METRIC) {
      return fail(400, "ST001", "metric: VISIT_COUNT 만 지원합니다.");
    }

    const bbox = parseBbox(params.get("bbox"));
    const zoom = parseZoom(params.get("zoom"));
    const from = parseDate(params.get("from"), "from");
    const to = parseDate(params.get("to"), "to");

    if ("error" in bbox) {
      return bbox.error;
    }

    if ("error" in zoom) {
      return zoom.error;
    }

    if ("error" in from) {
      return from.error;
    }

    if ("error" in to) {
      return to.error;
    }

    if (category !== null && findCategory(category) === null) {
      return fail(400, "C003", `category: 없는 카테고리입니다: ${category}`);
    }

    if (from.value !== null && to.value !== null && from.value > to.value) {
      return fail(400, "C012", "from 이 to 보다 늦습니다.");
    }

    const multiplier = multiplierFor(zoom.value);
    const size = BASE_CELL_DEGREES * multiplier;
    const korea = intersect(bbox.value, KOREA);
    let cells: VisitCellDto[] = [];

    if (korea !== null) {
      const area = expandToCells(korea, size);
      const cellCount = Math.ceil((area.east - area.west) / size) * Math.ceil((area.north - area.south) / size);

      if (cellCount > MAX_CELLS) {
        return fail(400, "C013", "bbox: 조회 영역이 너무 넓습니다. zoom 을 낮추거나 영역을 줄여 주세요.");
      }

      cells = toCells(countedFootprints(from.value, to.value), area, size, multiplier, category);
    }

    const response: VisitStatisticsResponseDto = {
      metric: VISIT_COUNT_METRIC,
      period: { from: from.value, to: to.value },
      aggregation: { unit: AGGREGATION_UNIT, version: AGGREGATION_VERSION, precision: `${roundCoordinate(size)}deg`, minCount: MIN_COUNT },
      cells,
      updatedAt: formatServerDateTime(new Date()),
    };

    return ok(response);
  }),
];
