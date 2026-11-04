import { getErrorMessage, isApiError } from "@/api/errors";

/** 코스(CO) 도메인 code별 문구. 공통 code는 `api/errors.ts`가 먼저 처리한다. */
const COURSE_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  CO001: "공개가 해제되었거나 삭제된 코스예요.",
  CO002: "지원하지 않는 검색 조건이에요. 조건을 바꿔 다시 검색해 주세요.",
  CO003: "지원하지 않는 정렬 조건이에요.",
  CO004: "지원하지 않는 카테고리예요.",
  CO005: "추천 코스를 잠시 불러올 수 없어요. 잠시 후 다시 시도해 주세요.",
};

export const COURSE_ERROR_FALLBACK = "잠시 후 다시 시도해 주세요.";

export function getCourseErrorMessage(error: unknown): string {
  return getErrorMessage(error, COURSE_ERROR_MESSAGES, COURSE_ERROR_FALLBACK);
}

/** 요청한 공개 코스에 접근할 수 없음(없음·삭제·공개 해제). */
export function isCourseUnavailable(error: unknown): boolean {
  return isApiError(error) && error.code === "CO001";
}
