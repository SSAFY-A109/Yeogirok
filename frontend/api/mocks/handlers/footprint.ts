import { delay, http } from "msw";

import {
  countFootprintBody,
  FOOTPRINT_BODY_MAX_LENGTH,
  FOOTPRINT_CATEGORY_MAX_COUNT,
  FOOTPRINT_CATEGORY_MIN_COUNT,
  isValidCoordinate,
  normalizeGuestbookBody,
  type CreateFootprintResponseDto,
} from "../../../features/footprint/model";
import { formatServerDateTime, parseServerDateTime } from "../../../lib/date";
import { mockDb } from "../db";
import { findCategory } from "../fixtures/common";
import { mockDateTime, type MockFootprint } from "../fixtures/footprint";
import { fail, MOCK_DELAY_MS, ok, readId, requireMockBearer } from "./respond";

// API-041 목. backend/dev(90aaede) FootprintController·FootprintCreateService·FootprintCreateRequest의 검사 순서를 따른다.
// 형식 검사 → 멱등키(같은 내용이면 저장한 결과, 다른 내용이면 C005) → 값 검사 → 같은 날 가까운 발자취(F003) → 저장.

/** 목 전용 위치 증거 유효 시간. 서버 값(`yeogirok.location-evidence.max-age`)은 설정이다(DEC-01). 개발 서버는 2분이고 백엔드가 늘릴 예정이라(2026-11-06 답변) 목은 넉넉히 둔다. */
const MOCK_EVIDENCE_MAX_AGE_MS = 10 * 60_000;
/** 기기 시계가 이만큼 앞서도 받아 준다(서버 `max-future-skew`의 목 값). */
const MOCK_EVIDENCE_MAX_FUTURE_MS = 60_000;
/** 같은 날 이 거리(m) 안에 다시 남기면 F003. 서버 설정 `same-day-radius-meters`의 오류 문구 값(500m)을 따랐다. */
const MOCK_SAME_DAY_RADIUS_M = 500;
/** 작성 제한 구간이 바뀌는 시각(Asia/Seoul 04:00). */
const DAY_START_HOUR = 4;
const SEOUL_OFFSET_MS = 9 * 3_600_000;
const HOUR_MS = 3_600_000;
const DAY_MS = 86_400_000;
const EARTH_RADIUS_M = 6_371_000;

type FootprintCommand = {
  latitude: number;
  longitude: number;
  capturedAt: Date;
  body: string | null;
  categoryCodes: string[];
};

type ParseResult = { command: FootprintCommand } | { error: ReturnType<typeof fail> };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function evidenceMissing(field: string) {
  return { error: fail(422, "C008", `${field}: 현재 위치 증거가 필요합니다.`) };
}

function invalidFormat(field: string) {
  return { error: fail(400, "C002", `${field}: 형식이 올바르지 않습니다.`) };
}

function readCapturedAt(value: unknown): Date | null {
  try {
    return parseServerDateTime(value, "location.capturedAt");
  } catch {
    return null;
  }
}

/** 서버 `FootprintCreateRequest.toCommand`와 같은 형식 검사. */
function parseCommand(value: unknown): ParseResult {
  if (!isRecord(value)) {
    return invalidFormat("request");
  }

  const { location, body, categoryCodes } = value;

  if (location === undefined || location === null) {
    return evidenceMissing("location");
  }

  if (!isRecord(location)) {
    return invalidFormat("location");
  }

  for (const field of ["latitude", "longitude", "capturedAt"]) {
    if (location[field] === undefined || location[field] === null) {
      return evidenceMissing(`location.${field}`);
    }
  }

  if (typeof location.latitude !== "number" || typeof location.longitude !== "number") {
    return invalidFormat("location.latitude·longitude");
  }

  const capturedAt = readCapturedAt(location.capturedAt);

  if (capturedAt === null) {
    return invalidFormat("location.capturedAt");
  }

  if (body !== undefined && body !== null && typeof body !== "string") {
    return invalidFormat("body");
  }

  if (categoryCodes === undefined || categoryCodes === null) {
    return { error: fail(400, "C001", "categoryCodes: 값이 필요합니다.") };
  }

  if (!Array.isArray(categoryCodes) || !categoryCodes.every((code) => {
    return typeof code === "string";
  })) {
    return invalidFormat("categoryCodes");
  }

  return {
    command: {
      latitude: location.latitude,
      longitude: location.longitude,
      capturedAt,
      body: typeof body === "string" ? body : null,
      categoryCodes,
    },
  };
}

/** 서버 `FootprintBody.normalize`: CRLF·CR → LF, 공백만이면 `null`. */
function normalizeBody(body: string | null): string | null {
  if (body === null) {
    return null;
  }

  const normalized = normalizeGuestbookBody(body);
  return normalized.trim() === "" ? null : normalized;
}

/** 값 검사(서버 C011·C003·C009). 통과하면 `null`. */
function validateCommand(command: FootprintCommand, now: number) {
  if (!isValidCoordinate(command.latitude, command.longitude)) {
    return fail(400, "C011", "위도·경도가 허용 범위를 벗어났습니다.");
  }

  const codes = command.categoryCodes;

  if (codes.length < FOOTPRINT_CATEGORY_MIN_COUNT || codes.length > FOOTPRINT_CATEGORY_MAX_COUNT) {
    return fail(400, "C003", "categoryCodes: 1~3개를 골라야 합니다.");
  }

  if (new Set(codes).size !== codes.length || codes.some((code) => {
    return findCategory(code) === null;
  })) {
    return fail(400, "C003", "categoryCodes: 없거나 중복된 카테고리입니다.");
  }

  const body = normalizeBody(command.body);

  if (body !== null && countFootprintBody(body) > FOOTPRINT_BODY_MAX_LENGTH) {
    return fail(400, "C003", "body: 최대 200자까지 쓸 수 있습니다.");
  }

  const capturedAt = command.capturedAt.getTime();

  if (capturedAt < now - MOCK_EVIDENCE_MAX_AGE_MS || capturedAt > now + MOCK_EVIDENCE_MAX_FUTURE_MS) {
    return fail(422, "C009", "현재 위치 증거의 유효 시각이 지났습니다.");
  }

  return null;
}

/** 서버 `dayStartOf`: now가 속한 작성 제한 구간의 시작(Asia/Seoul 04:00). */
function dayStartOf(now: number): number {
  const seoul = new Date(now + SEOUL_OFFSET_MS);
  const midnight = Date.UTC(seoul.getUTCFullYear(), seoul.getUTCMonth(), seoul.getUTCDate());
  const start = midnight + DAY_START_HOUR * HOUR_MS - SEOUL_OFFSET_MS;
  return now < start ? start - DAY_MS : start;
}

function toRadians(degree: number) {
  return (degree * Math.PI) / 180;
}

function distanceMeters(aLat: number, aLng: number, bLat: number, bLng: number): number {
  const dLat = toRadians(bLat - aLat);
  const dLng = toRadians(bLng - aLng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRadians(aLat)) * Math.cos(toRadians(bLat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
}

function isTooCloseToday(command: FootprintCommand, now: number): boolean {
  const start = dayStartOf(now);

  return [...mockDb.footprints.values()].some((footprint) => {
    const createdAt = parseServerDateTime(footprint.createdAt, "createdAt").getTime();

    return footprint.ownerId === 1 && createdAt >= start && createdAt < start + DAY_MS &&
      distanceMeters(footprint.latitude, footprint.longitude, command.latitude, command.longitude) <= MOCK_SAME_DAY_RADIUS_M;
  });
}

function toRequestHash(command: FootprintCommand): string {
  return JSON.stringify({ ...command, capturedAt: command.capturedAt.toISOString() });
}

function ownFootprint(value: string | readonly string[] | undefined) {
  const id = readId(value);
  const item = id === null ? undefined : mockDb.footprints.get(id);

  if (!item) {
    return fail(404, "F001", "발자취 없음");
  }

  return item.ownerId === 1 ? item : fail(403, "F002", "작성자 본인이 아님");
}

function publicFields({ ownerId: _ownerId, ...item }: MockFootprint) {
  return item;
}

function validDate(value: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && new Date(value).toJSON()?.slice(0, 10) === value;
}

export const footprintHandlers = [
  http.post("*/api/v1/footprints", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const idempotencyKey = request.headers.get("Idempotency-Key")?.trim() ?? "";

    if (idempotencyKey === "") {
      return fail(400, "C001", "Idempotency-Key 헤더가 필요합니다.");
    }

    const parsed = parseCommand(await request.json());

    if ("error" in parsed) {
      return parsed.error;
    }

    const { command } = parsed;
    const requestHash = toRequestHash(command);
    const previous = mockDb.footprintRequests.get(idempotencyKey);

    if (previous) {
      if (previous.requestHash !== requestHash) {
        return fail(409, "C005", "같은 멱등키로 다른 내용의 요청을 보냈습니다.");
      }

      return mockDb.footprints.has(previous.response.id) ? ok(previous.response, 201) : fail(404, "F001", "발자취를 찾을 수 없습니다.");
    }

    const now = Date.now();
    const invalid = validateCommand(command, now);

    if (invalid) {
      return invalid;
    }

    if (isTooCloseToday(command, now)) {
      return fail(409, "F003", "오늘(04:00 기준) 반경 500m 안에 이미 남긴 발자취가 있습니다.");
    }

    const footprint: CreateFootprintResponseDto = {
      id: mockDb.nextFootprintId,
      latitude: command.latitude,
      longitude: command.longitude,
      body: normalizeBody(command.body),
      categoryCodes: [...command.categoryCodes],
      visitedAt: formatServerDateTime(command.capturedAt),
      createdAt: formatServerDateTime(new Date(now)),
      statisticsStatus: "APPLIED",
    };

    mockDb.nextFootprintId += 1;
    const { statisticsStatus: _statisticsStatus, ...fields } = footprint;
    mockDb.footprints.set(footprint.id, { ...fields, ownerId: 1, version: 1, locationName: null, address: null });
    mockDb.footprintRequests.set(idempotencyKey, { requestHash, response: footprint });
    return ok(footprint, 201);
  }),
  http.get("*/api/v1/users/me/footprints", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const params = new URL(request.url).searchParams;
    const from = params.get("from");
    const to = params.get("to");
    const bbox = params.get("bbox");
    const category = params.get("category");
    const cursor = Number(params.get("cursor") ?? 0);
    const limit = Number(params.get("limit") ?? 20);
    const bounds = bbox?.split(",").map(Number);

    if (bounds && (bounds.length !== 4 || !bounds.every(Number.isFinite) || bounds[0] < -180 || bounds[2] > 180 || bounds[1] < -90 || bounds[3] > 90 || bounds[0] > bounds[2] || bounds[1] > bounds[3])) {
      return fail(400, "C013", "지도 조회 영역 오류");
    }

    if ((from !== null && !validDate(from)) || (to !== null && !validDate(to)) || (from !== null && to !== null && from > to)) {
      return fail(400, "C012", "날짜 범위 오류");
    }

    if ((category !== null && !findCategory(category)) || !Number.isSafeInteger(cursor) || cursor < 0 || !Number.isSafeInteger(limit) || limit < 1 || limit > 50) {
      return fail(400, "C003", "지원하지 않는 조회 조건");
    }

    const matches = [...mockDb.footprints.values()].filter((item) => {
      const day = item.visitedAt.slice(0, 10);
      return item.ownerId === 1 && (!from || day >= from) && (!to || day <= to)
        && (!category || item.categoryCodes.includes(category))
        && (!bounds || (item.longitude >= bounds[0] && item.latitude >= bounds[1] && item.longitude <= bounds[2] && item.latitude <= bounds[3]));
    }).sort((a, b) => {
      return b.visitedAt.localeCompare(a.visitedAt) || b.id - a.id;
    });

    const items = matches.slice(cursor, cursor + limit).map(({ ownerId: _owner, createdAt: _created, version: _version, ...item }) => {
      return item;
    });

    return ok({ items, nextCursor: cursor + limit < matches.length ? String(cursor + limit) : null });
  }),
  http.get("*/api/v1/footprints/:footprintId", async ({ request, params }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const item = ownFootprint(params.footprintId);
    return "ownerId" in item ? ok(publicFields(item)) : item;
  }),
  http.patch("*/api/v1/footprints/:footprintId", async ({ request, params }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const input = await request.json().catch(() => {
      return null;
    });

    if (!input || typeof input !== "object" || !("version" in input) || !Number.isSafeInteger(input.version)) {
      return fail(400, "C002", "요청 형식 오류");
    }

    if (!("body" in input)) {
      return fail(400, "C001", "본문 필드 누락");
    }

    if (typeof input.body !== "string") {
      return fail(400, "C002", "본문 형식 오류");
    }

    if (countFootprintBody(input.body) > FOOTPRINT_BODY_MAX_LENGTH) {
      return fail(400, "C003", "본문 길이 초과");
    }

    const item = ownFootprint(params.footprintId);

    if (!("ownerId" in item)) {
      return item;
    }

    if (item.version !== input.version) {
      return fail(409, "C004", "버전 불일치");
    }

    item.body = input.body.trim() ? normalizeGuestbookBody(input.body) : null;
    item.version += 1;
    return ok({ id: item.id, body: item.body, updatedAt: mockDateTime(), version: item.version });
  }),
  http.delete("*/api/v1/footprints/:footprintId", async ({ request, params }) => {
    await delay(MOCK_DELAY_MS);
    const authError = requireMockBearer(request);

    if (authError) {
      return authError;
    }

    const item = ownFootprint(params.footprintId);

    if (!("ownerId" in item)) {
      return item;
    }

    const version = new URL(request.url).searchParams.get("version");

    if (version === null || Number(version) !== item.version) {
      return fail(409, "C004", "버전 불일치");
    }

    mockDb.footprints.delete(item.id);
    return ok({ deleted: true, statisticsStatus: "APPLIED" });
  }),
];
