import { assertId, ContractMismatchError, toKnownEnum } from "../../lib/contract";
import { formatServerDateTime, formatTimeOfDay, parseServerDateTime } from "../../lib/date";

// API-041 발자취·방명록 저장. 2026-11-06 backend/dev(90aaede, PR #46)의 FootprintCreateRequest·FootprintCreateResponse·
// FootprintBody·FootprintCategories에서 옮겼다. Swagger(k15a109)는 502로 응답이 없어 대조하지 못했다(TBD).
// 위치 증거는 좌표와 측정 시각만 받는다. 정확도(m) 필드는 계약에 없어 보내지 않는다(규칙 80과 다름, TBD).

/** 방명록 본문 최대 길이. 서버처럼 줄바꿈을 LF로 맞춘 뒤 코드 포인트 수로 센다. */
export const FOOTPRINT_BODY_MAX_LENGTH = 200;

/** 고를 수 있는 카테고리 수(서버 `FootprintCategories`). */
export const FOOTPRINT_CATEGORY_MIN_COUNT = 1;
export const FOOTPRINT_CATEGORY_MAX_COUNT = 3;

const MAX_LATITUDE = 90;
const MAX_LONGITUDE = 180;

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

export type FootprintDraft = {
  /** 기기가 잰 위치. 측정 시각을 위치 증거로 보낸다. */
  position: FootprintPosition;
  /**
   * 사용자가 지도에서 직접 고른 위치(2026-11-06 사용자 결정, 거리 제한 없음). 없으면 잰 위치를 그대로 쓴다.
   * 서버는 고른 위치인지 알 수 없어 잰 위치처럼 저장한다. 고른 여부·원래 측정 좌표를 받을 필드는 백엔드에 요청할 일이다(TBD).
   */
  chosenPoint: FootprintPoint | null;
  categoryCodes: readonly string[];
  body: string;
};

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
  return [...body.replace(/\r\n?/g, "\n")].length;
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

/**
 * 입력을 저장 요청으로 바꾼다. 카테고리 수·본문 길이·좌표 범위가 맞지 않으면 `null`이다.
 * 공백만 쓴 본문은 서버처럼 `null`로 보낸다.
 */
export function toCreateFootprintRequest({ position, chosenPoint, categoryCodes, body }: FootprintDraft): CreateFootprintRequestDto | null {
  const point = chosenPoint ?? position;

  if (
    !isValidCoordinate(point.latitude, point.longitude) ||
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
      capturedAt: formatServerDateTime(position.measuredAt),
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
