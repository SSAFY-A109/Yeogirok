import { assertId, ContractMismatchError, toKnownEnum } from "../../lib/contract";
import { formatServerDateTime, formatTimeOfDay, localDateParts, makeLocalDate, parseServerDateTime, type LocalDate } from "../../lib/date";

// API-041 발자취·방명록 저장. 2026-11-06 backend/dev(90aaede, PR #46)의 FootprintCreateRequest·FootprintCreateResponse·
// FootprintBody·FootprintCategories에서 옮겼다. Swagger(k15a109)는 502로 응답이 없어 대조하지 못했다(TBD).
// 위치 증거는 좌표와 측정 시각만 받는다. 정확도(m) 필드는 계약에 없어 보내지 않는다(규칙 80과 다름, TBD).

/** 방명록 본문 최대 길이. 서버처럼 줄바꿈을 LF로 맞춘 뒤 코드 포인트 수로 센다. */
export const FOOTPRINT_BODY_MAX_LENGTH = 200;

/** 고를 수 있는 카테고리 수(서버 `FootprintCategories`). */
export const FOOTPRINT_CATEGORY_MIN_COUNT = 1;
export const FOOTPRINT_CATEGORY_MAX_COUNT = 3;

/**
 * 잰 위치에서 이 거리(m) 안에서만 발자취 위치를 직접 고칠 수 있다(2026-11-11 사용자 결정).
 * 서버는 고친 좌표만 받아 이 제한을 확인하지 못한다. 앱에서만 막는 값이다(TBD: 원래 측정 좌표 필드는 백엔드와 정한다).
 */
export const FOOTPRINT_ADJUST_RADIUS_METERS = 100;

const MAX_LATITUDE = 90;
const MAX_LONGITUDE = 180;
const EARTH_RADIUS_METERS = 6_371_000;
const RADIANS_PER_DEGREE = Math.PI / 180;

export type FootprintLocationEvidenceDto = {
  latitude: number;
  longitude: number;
  /** 기기가 위치를 잰 시각(`2026-11-06 15:30:00+09`). 유효 시간은 서버가 판정한다(C009). */
  capturedAt: string;
};

export type CreateFootprintRequestDto = {
  location: FootprintLocationEvidenceDto;
  /** 방명록 본문. 쓰지 않으면 `null`. 작성자 본인만 본다. */
  body: string | null;
  categoryCodes: string[];
};

const STATISTICS_STATUSES = ["PENDING", "APPLIED", "UNCHANGED"] as const;

export type StatisticsStatusDto = (typeof STATISTICS_STATUSES)[number];

export type CreateFootprintResponseDto = {
  id: number;
  latitude: number;
  longitude: number;
  body: string | null;
  categoryCodes: string[];
  visitedAt: string;
  createdAt: string;
  statisticsStatus: StatisticsStatusDto;
};

/** 화면에서 위치 증거로 쓰는 측정 결과. `lib/native/location`의 `MeasuredPosition`과 같은 모양이다. */
export type FootprintPosition = {
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
  measuredAt: Date;
};

/** 발자취를 남길 좌표. */
export type FootprintPoint = { latitude: number; longitude: number };

/** 사용자가 지도에서 직접 고친 위치와 그 위치를 확정한 시각. */
export type ChosenFootprintPoint = { point: FootprintPoint; chosenAt: Date };

export type FootprintDraft = {
  /** 기기가 잰 위치. */
  position: FootprintPosition;
  /**
   * 사용자가 지도에서 직접 고친 위치(2026-11-06 사용자 결정). 없으면 잰 위치를 그대로 쓴다.
   * 잰 위치에서 `FOOTPRINT_ADJUST_RADIUS_METERS` 안에서만 고칠 수 있다(2026-11-11 사용자 결정).
   * 백엔드 답변(2026-11-06): 고친 위치를 사용자의 실제 위치로 보고 그대로 저장·주소·500m 작성 제한·방문 분포에 반영한다.
   * 고친 여부·원래 측정 좌표 필드는 아직 계약에 없다(TBD).
   */
  chosen: ChosenFootprintPoint | null;
  categoryCodes: readonly string[];
  body: string;
};

/**
 * 위치 증거로 보낼 시각(`capturedAt`, 서버가 방문 시각으로도 저장한다). 백엔드 답변대로 처음 잰 시각이나 핀을 확정한 시각을
 * 보낼 수 있어, 둘 중 늦은 쪽을 쓴다. 핀을 고치고 글을 쓰다 유효 시간(C009, 개발 서버 2분)을 넘기면 "위치 다시 확인"으로 새로 잰다.
 */
function evidenceTime({ position, chosen }: Pick<FootprintDraft, "position" | "chosen">): Date {
  return chosen !== null && chosen.chosenAt > position.measuredAt ? chosen.chosenAt : position.measuredAt;
}

export type SavedFootprint = {
  id: number;
  visitedAt: Date;
  statisticsStatus: StatisticsStatusDto | "UNKNOWN";
};

/** 측정 결과를 "정확도 12m · 15:07 확인"처럼 적는다. 정확도를 모르면 시각만 적는다. */
export function formatLocationEvidence({ accuracyMeters, measuredAt }: FootprintPosition): string {
  const checkedAt = `${formatTimeOfDay(measuredAt)} 확인`;
  return accuracyMeters === null ? checkedAt : `정확도 ${Math.round(accuracyMeters)}m · ${checkedAt}`;
}

/** 서버와 같은 기준으로 방명록 글자 수를 센다(CRLF·CR → LF, 코드 포인트). */
export function countFootprintBody(body: string): number {
  return [...normalizeGuestbookBody(body)].length;
}

export function isFootprintBodyTooLong(body: string): boolean {
  return countFootprintBody(body) > FOOTPRINT_BODY_MAX_LENGTH;
}

export function isValidCategoryCount(count: number): boolean {
  return count >= FOOTPRINT_CATEGORY_MIN_COUNT && count <= FOOTPRINT_CATEGORY_MAX_COUNT;
}

/** 위경도 범위를 요청 전에 확인한다(서버 C011 예방). */
export function isValidCoordinate(latitude: number, longitude: number): boolean {
  return Number.isFinite(latitude) && Number.isFinite(longitude) && Math.abs(latitude) <= MAX_LATITUDE && Math.abs(longitude) <= MAX_LONGITUDE;
}

/** 두 좌표 사이의 거리(m). 지구를 구로 보고 잰다(하버사인). */
function distanceMeters(from: FootprintPoint, to: FootprintPoint): number {
  const latitudeDelta = (to.latitude - from.latitude) * RADIANS_PER_DEGREE;
  const longitudeDelta = (to.longitude - from.longitude) * RADIANS_PER_DEGREE;

  const haversine = Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(from.latitude * RADIANS_PER_DEGREE) * Math.cos(to.latitude * RADIANS_PER_DEGREE) * Math.sin(longitudeDelta / 2) ** 2;

  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(haversine)));
}

/** 고른 곳이 잰 위치에서 직접 고칠 수 있는 거리 안인지. */
export function isWithinAdjustRadius(measured: FootprintPoint, point: FootprintPoint): boolean {
  return distanceMeters(measured, point) <= FOOTPRINT_ADJUST_RADIUS_METERS;
}

/**
 * 입력을 저장 요청으로 바꾼다. 카테고리 수·본문 길이·좌표 범위가 맞지 않거나, 직접 고친 위치가 잰 위치에서 너무 멀면 `null`이다.
 * 공백만 쓴 본문은 서버처럼 `null`로 보낸다.
 */
export function toCreateFootprintRequest({ position, chosen, categoryCodes, body }: FootprintDraft): CreateFootprintRequestDto | null {
  const point = chosen?.point ?? position;

  if (
    !isValidCoordinate(point.latitude, point.longitude) ||
    (chosen !== null && !isWithinAdjustRadius(position, chosen.point)) ||
    !isValidCategoryCount(categoryCodes.length) ||
    new Set(categoryCodes).size !== categoryCodes.length ||
    isFootprintBodyTooLong(body)
  ) {
    return null;
  }

  return {
    location: {
      latitude: point.latitude,
      longitude: point.longitude,
      capturedAt: formatServerDateTime(evidenceTime({ position, chosen })),
    },
    body: body.trim() === "" ? null : body,
    categoryCodes: [...categoryCodes],
  };
}

export function toSavedFootprint(dto: CreateFootprintResponseDto): SavedFootprint {
  if (typeof dto !== "object" || dto === null) {
    throw new ContractMismatchError("footprint");
  }

  return {
    id: assertId(dto.id, "footprint.id"),
    visitedAt: parseServerDateTime(dto.visitedAt, "footprint.visitedAt"),
    statisticsStatus: toKnownEnum(dto.statisticsStatus, STATISTICS_STATUSES),
  };
}

export type FootprintFilters = { bbox?: string; from?: LocalDate; to?: LocalDate; category?: string };
export type FootprintListParams = FootprintFilters & { cursor?: string; limit?: number };
export type FootprintDto = {
  id: number;
  latitude: number;
  longitude: number;
  locationName: string | null;
  address: string | null;
  body: string | null;
  categoryCodes: string[];
  visitedAt: string;
};
export type FootprintListDto = { items: FootprintDto[]; nextCursor: string | null };
export type FootprintDetailDto = FootprintDto & { createdAt: string; version: number };
export type UpdateGuestbookDto = { version: number; body: string };
export type GuestbookUpdateDto = { id: number; body: string | null; updatedAt: string; version: number };
export type FootprintDeleteDto = { deleted: true; statisticsStatus: string };

export function normalizeGuestbookBody(body: string) {
  return body.replace(/\r\n?/g, "\n");
}

const VISIT_DATE_FORMAT = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "long", day: "numeric", weekday: "short", hour: "2-digit", minute: "2-digit", hour12: false });

export function formatFootprintDate(date: Date) {
  return VISIT_DATE_FORMAT.format(date);
}

/** 목록의 from/to는 서버와 동일하게 한국 날짜로 계산한다. */
export function footprintPeriod(period: "recent" | "month" | "all", now = new Date()): FootprintFilters {
  if (period === "all") {
    return {};
  }

  const to = new Date(now.getTime() + 9 * 3_600_000).toISOString().slice(0, 10);
  const { year, month, day } = localDateParts(to);
  const previousMonth = month - (period === "month" ? 1 : 3);
  const first = new Date(Date.UTC(year, previousMonth - 1, 1));
  const lastDay = new Date(Date.UTC(year, previousMonth, 0)).getUTCDate();
  return { from: makeLocalDate(first.getUTCFullYear(), first.getUTCMonth() + 1, Math.min(day, lastDay)), to };
}

function coordinate(value: unknown, max: number, field: string) {
  if (typeof value !== "number" || !Number.isFinite(value) || Math.abs(value) > max) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function nullableText(value: unknown, field: string) {
  if (value !== null && typeof value !== "string") {
    throw new ContractMismatchError(field);
  }

  return value;
}

function toFootprint(dto: FootprintDto) {
  if (!Array.isArray(dto.categoryCodes) || dto.categoryCodes.some((code) => {
    return typeof code !== "string";
  })) {
    throw new ContractMismatchError("categoryCodes");
  }

  return {
    id: assertId(dto.id, "id"),
    lat: coordinate(dto.latitude, 90, "latitude"),
    lng: coordinate(dto.longitude, 180, "longitude"),
    place: nullableText(dto.locationName, "locationName"),
    address: nullableText(dto.address, "address"),
    body: nullableText(dto.body, "body"),
    categoryCodes: dto.categoryCodes,
    visitedAt: parseServerDateTime(dto.visitedAt, "visitedAt"),
  };
}

export type Footprint = ReturnType<typeof toFootprint>;

export function toFootprintList(dto: FootprintListDto) {
  return { items: dto.items.map(toFootprint), nextCursor: nullableText(dto.nextCursor, "nextCursor") };
}

export function toFootprintDetail(dto: FootprintDetailDto) {
  return { ...toFootprint(dto), createdAt: parseServerDateTime(dto.createdAt, "createdAt"), version: assertId(dto.version, "version") };
}

export type FootprintDetail = ReturnType<typeof toFootprintDetail>;

export function toGuestbookUpdate(dto: GuestbookUpdateDto) {
  return {
    id: assertId(dto.id, "id"),
    body: nullableText(dto.body, "body"),
    updatedAt: parseServerDateTime(dto.updatedAt, "updatedAt"),
    version: assertId(dto.version, "version"),
  };
}

export function toFootprintDelete(dto: FootprintDeleteDto) {
  if (dto.deleted !== true) {
    throw new ContractMismatchError("deleted");
  }

  return { deleted: true, statisticsStatus: toKnownEnum(dto.statisticsStatus, STATISTICS_STATUSES) };
}
