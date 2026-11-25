import { getErrorMessage, isApiError } from "@/api/errors";
import type { LocationFailure } from "@/lib/native/location";

// 2026-11-06 backend/dev `ExceptionType` 기준 code다. `api/errors.ts`의 공통 표도 2026-11-10에 같은 서버 번호로 맞췄다.
// 이 표에는 발자취 저장 상황에 맞춘 문구를 둔다. 같은 code가 공통 표에 있어도 이 표의 문구를 먼저 쓴다.

/** 위치를 다시 재면 풀리는 code: 위치 증거 없음(C008)·유효 시각 지남(C009)·위경도 범위(C011). */
const LOCATION_EVIDENCE_CODES: readonly string[] = ["C008", "C009", "C011"];

const FOOTPRINT_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  C003: "카테고리와 방명록 내용을 확인해 주세요.",
  C005: "저장 요청을 처리하지 못했어요. 다시 시도해 주세요.",
  C008: "현재 위치를 확인하지 못했어요. 위치를 다시 확인해 주세요.",
  C009: "위치를 확인한 지 오래됐어요. 위치를 다시 확인해 주세요.",
  C011: "위치 값이 올바르지 않아요. 위치를 다시 확인해 주세요.",
  C018: "발자취를 남기고 있어요. 잠시 후 다시 시도해 주세요.",
  F003: "오늘 이 근처에 이미 발자취를 남겼어요. 다른 곳에서 남겨 주세요.",
  NETWORK: "인터넷 연결이 불안정해 저장됐는지 확인하지 못했어요. 다시 시도해도 두 번 저장되지 않아요.",
  TIMEOUT: "응답이 늦어 저장됐는지 확인하지 못했어요. 다시 시도해도 두 번 저장되지 않아요.",
};

export const FOOTPRINT_SAVE_FALLBACK = "발자취를 남기지 못했어요. 다시 시도해 주세요.";

export function getFootprintErrorMessage(error: unknown): string {
  if (isApiError(error) && error.code in FOOTPRINT_ERROR_MESSAGES) {
    return FOOTPRINT_ERROR_MESSAGES[error.code];
  }

  return getErrorMessage(error, FOOTPRINT_ERROR_MESSAGES, FOOTPRINT_SAVE_FALLBACK);
}

const LOCATION_FAILURE_MESSAGES: Readonly<Record<LocationFailure, string>> = {
  "services-off": "기기의 위치 서비스가 꺼져 있어요. 위치 서비스를 켠 뒤 다시 확인해 주세요.",
  permission: "위치 권한이 없어 지금 있는 곳을 확인하지 못했어요.",
  timeout: "위치 신호를 받지 못했어요. 하늘이 트인 곳에서 다시 확인해 주세요.",
  unavailable: "지금 있는 곳을 확인하지 못했어요. 잠시 후 다시 확인해 주세요.",
};

/** 기기에서 위치를 재지 못한 이유를 사용자 문구로 바꾼다. */
export function getLocationFailureMessage(failure: LocationFailure): string {
  return LOCATION_FAILURE_MESSAGES[failure];
}

/** 위치를 다시 재야 하는 오류인지. 자동으로 다시 보내지 않고 사용자가 다시 재게 한다(규칙 20). */
export function isLocationEvidenceError(error: unknown): boolean {
  return isApiError(error) && LOCATION_EVIDENCE_CODES.includes(error.code);
}

const MY_FOOTPRINT_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  F001: "발자취가 없거나 삭제됐어요.",
  F002: "내가 남긴 발자취만 볼 수 있어요.",
  C002: "입력한 값의 형식을 확인해 주세요.",
  C003: "입력값이나 방명록 길이를 확인해 주세요.",
  C012: "조회 기간을 확인해 주세요.",
  C013: "지도 조회 영역을 확인해 주세요.",
};

export function isFootprintConflict(error: unknown) {
  return isApiError(error) && error.status === 409 && error.code === "C004";
}

export function isFootprintUnavailable(error: unknown) {
  return isApiError(error) && (error.status === 403 || error.status === 404);
}

export function getMyFootprintErrorMessage(error: unknown) {
  if (isFootprintConflict(error)) {
    return "다른 곳에서 발자취를 변경했어요. 최신 내용을 확인해 주세요.";
  }

  return isApiError(error) ? MY_FOOTPRINT_ERROR_MESSAGES[error.code] ?? getErrorMessage(error, {}, "다시 시도해 주세요.") : "발자취를 처리하지 못했어요. 다시 시도해 주세요.";
}
