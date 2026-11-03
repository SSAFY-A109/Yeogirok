import { getErrorMessage } from "@/api/errors";

/** 장소(P) 도메인 code별 문구. 도메인 code는 명세 미확정이라 아직 없다(TBD). */
const PLACE_ERROR_MESSAGES: Readonly<Record<string, string>> = {};

export const PLACE_SEARCH_ERROR_FALLBACK = "검색 결과를 불러오지 못했어요.";

export function getPlaceErrorMessage(error: unknown): string {
  return getErrorMessage(error, PLACE_ERROR_MESSAGES, PLACE_SEARCH_ERROR_FALLBACK);
}
