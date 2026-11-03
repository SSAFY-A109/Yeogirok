import { getErrorMessage } from "@/api/errors";

/** 계획 일정(SC) 도메인 code별 문구. 도메인 code는 명세 미확정이라 아직 없다(TBD). */
const SCHEDULE_ERROR_MESSAGES: Readonly<Record<string, string>> = {};

export const SCHEDULE_LOAD_ERROR_FALLBACK = "일정을 불러오지 못했어요.";

export function getScheduleErrorMessage(error: unknown): string {
  return getErrorMessage(error, SCHEDULE_ERROR_MESSAGES, SCHEDULE_LOAD_ERROR_FALLBACK);
}
