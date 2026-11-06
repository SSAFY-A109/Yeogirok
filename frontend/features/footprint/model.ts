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

/** 사용자가 지도에서 직접 고친 위치와 그 위치를 확정한 시각. */
export type ChosenFootprintPoint = { point: FootprintPoint; chosenAt: Date };

export type FootprintDraft = {
  /** 기기가 잰 위치. */
  position: FootprintPosition;
  /**
   * 사용자가 지도에서 직접 고친 위치(2026-11-06 사용자 결정, 거리 제한 없음). 없으면 잰 위치를 그대로 쓴다.
   * 백엔드 답변(2026-11-06): 고친 위치를 사용자의 실제 위치로 보고 그대로 저장·주소·500m 작성 제한·방문 분포에 반영한다.
   * 고친 여부·원래 측정 좌표 필드는 거리 제한이 생기면 그때 정한다.
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
export function toCreateFootprintRequest({ position, chosen, categoryCodes, body }: FootprintDraft): CreateFootprintRequestDto | null {
  const point = chosen?.point ?? position;

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
