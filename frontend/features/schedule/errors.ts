import { getErrorMessage, isApiError } from "@/api/errors";

/**
 * 계획 일정(SC) 도메인 code별 문구. 도메인 code는 명세 미확정이라 아직 없다(TBD).
 * C018은 백엔드 공통 멱등키 처리의 "같은 요청 처리 중"이다(`backend/dev` ExceptionType, 2026-11-04 확인).
 */
const SCHEDULE_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  C018: "같은 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요.",
};

export const SCHEDULE_ERROR_FALLBACK = {
  load: "일정을 불러오지 못했어요.",
  draft: "일정 초안을 만들지 못했어요.",
  save: "일정을 저장하지 못했어요.",
} as const;

export function getScheduleErrorMessage(error: unknown, fallback: string = SCHEDULE_ERROR_FALLBACK.load): string {
  return getErrorMessage(error, SCHEDULE_ERROR_MESSAGES, fallback);
}

/**
 * 초안 작업이 실패(status FAILED)했을 때 code별 문구. code는 명세 미확정이라 목 값만 둔다(TBD).
 * 모르는 code는 기본 문구로 보여 준다.
 */
const DRAFT_FAILURE_MESSAGES: Readonly<Record<string, string>> = {
  SC_NO_PLACES: "일정에 넣을 후보 장소가 없어요. 장소를 담은 뒤 다시 만들어 주세요.",
};

export function getDraftFailureMessage(code: string | null): string {
  return (code === null ? undefined : DRAFT_FAILURE_MESSAGES[code]) ?? "일정 초안을 만들지 못했어요. 다시 시도해 주세요.";
}

const VERSION_CONFLICT_CODE = "C008";

/** 그사이 일행이 일정을 바꿔 적용이 거절됐는지. */
export function isScheduleConflict(error: unknown): boolean {
  return isApiError(error) && error.code === VERSION_CONFLICT_CODE;
}
