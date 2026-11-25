import { getErrorMessage } from "@/api/errors";

/** 장소(P) 도메인 code별 문구. 번호는 팀 Notion "API 명세"의 API-026 실패 응답(2026-11-10)을 따른다. */
const PLACE_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  P002: "검색어를 확인해 주세요.",
  P003: "장소 정보를 가져오지 못했어요. 잠시 후 다시 시도해 주세요.",
};

export const PLACE_SEARCH_ERROR_FALLBACK = "검색 결과를 불러오지 못했어요.";

export function getPlaceErrorMessage(error: unknown): string {
  return getErrorMessage(error, PLACE_ERROR_MESSAGES, PLACE_SEARCH_ERROR_FALLBACK);
}
