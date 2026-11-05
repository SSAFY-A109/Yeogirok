import { getErrorMessage } from "@/api/errors";

/** 방문 통계(ST) 도메인 code별 문구. 도메인 code는 명세 미확정이라 아직 없다(TBD). */
const STATISTICS_ERROR_MESSAGES: Readonly<Record<string, string>> = {};

export const VISIT_STATISTICS_ERROR_FALLBACK = "방문 분포를 불러오지 못했어요.";

export function getStatisticsErrorMessage(error: unknown): string {
  return getErrorMessage(error, STATISTICS_ERROR_MESSAGES, VISIT_STATISTICS_ERROR_FALLBACK);
}
