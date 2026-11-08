import { getErrorMessage, isApiError } from "@/api/errors";

const MESSAGES: Readonly<Record<string, string>> = {
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

export function getFootprintErrorMessage(error: unknown) {
  if (isFootprintConflict(error)) {
    return "다른 곳에서 발자취를 변경했어요. 최신 내용을 확인해 주세요.";
  }

  return isApiError(error) ? MESSAGES[error.code] ?? getErrorMessage(error, {}, "다시 시도해 주세요.") : "발자취를 처리하지 못했어요. 다시 시도해 주세요.";
}
