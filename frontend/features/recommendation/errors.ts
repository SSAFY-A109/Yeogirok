import { getErrorMessage } from "@/api/errors";

/** AI 추천(AI) 도메인 code별 문구. 도메인 code는 명세 미확정이라 아직 없다(TBD). */
const RECOMMENDATION_ERROR_MESSAGES: Readonly<Record<string, string>> = {};

export const RECOMMENDATION_ERROR_FALLBACK = "추천을 만들지 못했어요";

export function getRecommendationErrorMessage(error: unknown): string {
  return getErrorMessage(error, RECOMMENDATION_ERROR_MESSAGES, RECOMMENDATION_ERROR_FALLBACK);
}
