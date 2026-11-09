import { ContractMismatchError } from "../../lib/contract";
import { formatMonthDay, parseNullableLocalDate, parseServerDateTime, type LocalDate } from "../../lib/date";

// API-046 카테고리별 방문 분포. 2026-11-06 origin/backend/dev(fc7b266)의 VisitStatisticsController·VisitStatisticsService·
// VisitStatisticsResponse·VisitGrid에서 옮겼다. Swagger(k15a109)는 502로 응답이 없어 대조하지 못했다(TBD).
// 서버가 지도 영역을 위경도 격자 칸으로 나눠 칸마다 발자취 수를 센다. 멀리 볼수록(zoom이 작을수록) 칸이 커져 여러 칸이 하나로 묶인다.
// 칸 크기·묶는 단계·최소 집계 수(DEC-07)는 서버 설정이라 클라이언트는 받은 칸을 그대로 그린다.

/** 서버가 받는 지표. 지금은 방문(발자취) 수 하나뿐이다. */
export const VISIT_COUNT_METRIC = "VISIT_COUNT";

const MAX_LATITUDE = 90;
const MAX_LONGITUDE = 180;
/** 카카오 지도 level을 웹 지도 zoom으로 바꿀 때의 기준. level 1(약 0.25m/px)이 zoom 19쯤이고 level이 1 오르면 축척이 2배라 zoom = 20 − level로 맞췄다(근사, TBD). */
const KAKAO_LEVEL_TO_ZOOM_BASE = 20;

export type VisitStatisticsParamsDto = {
  /** `west,south,east,north` */
  bbox: string;
  /** 웹 지도 zoom(0 이상). 서버가 이 값으로 칸 크기를 고른다. */
  zoom: number;
  /** API-079 카테고리 code. 없으면 전체. 주면 그 코드를 고른 발자취만 센다. */
  category?: string;
};

export type VisitCellDto = {
  cellId: string;
  center: { latitude: number; longitude: number };
  /** 이 칸에 남은 발자취 수(탈퇴자 보존분 포함). */
  count: number;
  /** 카테고리별 수. 여러 개 고른 발자취는 각 코드에 1씩 세므로 합이 `count`보다 클 수 있다. 0인 코드는 없다. */
  categoryCounts: Record<string, number>;
};

export type VisitStatisticsResponseDto = {
  metric: string;
  /** 요청한 기간. 기간을 보내지 않으면 둘 다 `null`(전체 기간). */
  period: { from: string | null; to: string | null } | null;
  aggregation: { unit: string; version: string; precision: string; minCount: number };
  cells: VisitCellDto[];
  /** 응답을 만든 시각. */
  updatedAt: string;
};

/** 지도 조회 영역. 남서(south·west)·북동(north·east) 좌표와 카카오 지도 확대 단계(level, 작을수록 가깝다). */
export type VisitMapArea = { south: number; west: number; north: number; east: number; level: number };

export type CategoryCount = { code: string; count: number };

/** 서버가 묶은 격자 칸 하나. 발자취 수와 카테고리별 수만 있고, 누가 남겼는지는 없다(규칙 80). */
export type VisitCell = {
  id: string;
  latitude: number;
  longitude: number;
  count: number;
  categoryCounts: CategoryCount[];
};

export type VisitStatistics = {
  cells: VisitCell[];
  /** 집계한 기간. 둘 다 `null`이면 전체 기간이다. */
  period: { from: LocalDate | null; to: LocalDate | null };
  /** 이 수보다 적게 남은 칸은 서버가 응답에서 뺐다(DEC-07). */
  minCount: number;
  updatedAt: Date;
};

function isLatitude(value: number) {
  return Number.isFinite(value) && Math.abs(value) <= MAX_LATITUDE;
}

function isLongitude(value: number) {
  return Number.isFinite(value) && Math.abs(value) <= MAX_LONGITUDE;
}

/** 조회 영역이 위경도 범위 안이고 남서가 북동보다 아래·왼쪽인지 확인한다(서버 C013 예방). */
export function isValidVisitMapArea(area: VisitMapArea): boolean {
  return (
    isLatitude(area.south) &&
    isLatitude(area.north) &&
    isLongitude(area.west) &&
    isLongitude(area.east) &&
    area.south < area.north &&
    area.west < area.east &&
    Number.isInteger(area.level) &&
    area.level > 0
  );
}

/** 집계 기간을 "전체 기간", "11월 1일 (토)부터"처럼 적는다. 기간 선택은 DEC-02 결정 전이라 서버가 준 값을 그대로 보여 준다. */
export function formatVisitPeriod({ from, to }: VisitStatistics["period"]): string {
  if (from !== null && to !== null) {
    return `${formatMonthDay(from)}~${formatMonthDay(to)}`;
  }

  if (from !== null) {
    return `${formatMonthDay(from)}부터`;
  }

  return to === null ? "전체 기간" : `${formatMonthDay(to)}까지`;
}

/** 카카오 지도 level을 서버가 받는 웹 지도 zoom으로 바꾼다. */
export function toMapZoom(level: number): number {
  return Math.max(0, KAKAO_LEVEL_TO_ZOOM_BASE - level);
}

export function toVisitStatisticsParams(area: VisitMapArea, categoryCode: string | null): VisitStatisticsParamsDto {
  return {
    bbox: [area.west, area.south, area.east, area.north].join(","),
    zoom: toMapZoom(area.level),
    category: categoryCode ?? undefined,
  };
}

function assertNumber(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function assertText(value: unknown, field: string): string {
  if (typeof value !== "string" || value.length === 0) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function toCategoryCounts(value: unknown): CategoryCount[] {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new ContractMismatchError("cells.categoryCounts");
  }

  return Object.entries(value).map(([code, count]) => {
    return { code, count: assertNumber(count, "cells.categoryCounts") };
  });
}

function toVisitCell(dto: VisitCellDto): VisitCell {
  const latitude = assertNumber(dto.center?.latitude, "cells.center.latitude");
  const longitude = assertNumber(dto.center?.longitude, "cells.center.longitude");

  if (!isLatitude(latitude) || !isLongitude(longitude)) {
    throw new ContractMismatchError("cells.center");
  }

  return {
    id: assertText(dto.cellId, "cells.cellId"),
    latitude,
    longitude,
    count: assertNumber(dto.count, "cells.count"),
    categoryCounts: toCategoryCounts(dto.categoryCounts),
  };
}

export function toVisitStatistics(dto: VisitStatisticsResponseDto): VisitStatistics {
  if (!Array.isArray(dto.cells)) {
    throw new ContractMismatchError("cells");
  }

  return {
    cells: dto.cells.map(toVisitCell),
    period: {
      from: parseNullableLocalDate(dto.period?.from ?? null, "period.from"),
      to: parseNullableLocalDate(dto.period?.to ?? null, "period.to"),
    },
    minCount: assertNumber(dto.aggregation?.minCount, "aggregation.minCount"),
    updatedAt: parseServerDateTime(dto.updatedAt, "updatedAt"),
  };
}
