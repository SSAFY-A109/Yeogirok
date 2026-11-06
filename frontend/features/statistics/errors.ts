import { getErrorMessage, isApiError } from "@/api/errors";

// 2026-11-06 backend/dev `ExceptionType`·`VisitStatisticsService` 기준 code다. `api/errors.ts`의 공통 표는 예전 번호라
// (예: 서버 C013 = 지도 조회 영역 오류인데 공통 표는 위치 증거 만료) 공통 표를 고치기 전까지(TBD) 이 표에서 먼저 찾는다.
const STATISTICS_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  C003: "고른 카테고리로 분포를 볼 수 없어요. 다른 카테고리를 골라 주세요.",
  C012: "기간을 확인해 주세요.",
  C013: "보이는 영역이 너무 넓어요. 지도를 조금 더 확대해 주세요.",
  ST001: "지금은 이 방식으로 분포를 볼 수 없어요.",
};

export const VISIT_STATISTICS_ERROR_FALLBACK = "방문 분포를 불러오지 못했어요.";

export function getStatisticsErrorMessage(error: unknown): string {
  if (isApiError(error) && error.code in STATISTICS_ERROR_MESSAGES) {
    return STATISTICS_ERROR_MESSAGES[error.code];
  }

  return getErrorMessage(error, STATISTICS_ERROR_MESSAGES, VISIT_STATISTICS_ERROR_FALLBACK);
}
