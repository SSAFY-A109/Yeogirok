import { getErrorMessage } from "@/api/errors";

/** AI 추천(AI) 도메인 code별 문구. 팀 Notion "API 명세"(2026-11-10)의 API-033·034에 도메인 code가 아직 없어 기본 문구만 쓴다(TBD). */
const RECOMMENDATION_ERROR_MESSAGES: Readonly<Record<string, string>> = {};

export const RECOMMENDATION_ERROR_FALLBACK = "추천을 만들지 못했어요";

export function getRecommendationErrorMessage(error: unknown): string {
  return getErrorMessage(error, RECOMMENDATION_ERROR_MESSAGES, RECOMMENDATION_ERROR_FALLBACK);
}
