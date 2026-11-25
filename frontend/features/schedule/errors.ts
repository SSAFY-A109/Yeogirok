import { getErrorMessage, isApiError } from "@/api/errors";

/** 계획 일정(SC)·여행방(RO) code별 문구. 번호는 백엔드 `ExceptionType`(backend/dev, 2026-11-10)과 같다. */
const SCHEDULE_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  SC001: "일정 초안을 찾을 수 없어요. 일정을 다시 만들어 주세요.",
  SC002: "종료 시각을 넘기는 날이 있어요. 머무는 시간을 줄이거나 장소를 다른 날로 옮긴 뒤 다시 시도해 주세요.",
  SC003: "아직 계산하고 있어요. 계산이 끝난 뒤 다시 시도해 주세요.",
  SC004: "만들지 못한 초안이에요. 일정을 다시 만들어 주세요.",
  RO001: "여행방을 찾을 수 없어요.",
  RO002: "참여 중인 여행방이 아니에요.",
};

export const SCHEDULE_ERROR_FALLBACK = {
  load: "일정을 불러오지 못했어요.",
  draft: "일정 초안을 만들지 못했어요.",
  recalculate: "다시 계산하지 못했어요.",
  save: "일정을 저장하지 못했어요.",
} as const;

export function getScheduleErrorMessage(error: unknown, fallback: string = SCHEDULE_ERROR_FALLBACK.load): string {
  return getErrorMessage(error, SCHEDULE_ERROR_MESSAGES, fallback);
}

/** 초안을 처음 만들다 실패했을 때(API-038 `error.code`) code별 문구. 모르는 code는 기본 문구로 보여 준다. */
const DRAFT_FAILURE_MESSAGES: Readonly<Record<string, string>> = {
  SC002: "정한 시간 안에 일정을 넣을 수 없었어요. 하루 시간을 늘려 다시 만들어 주세요.",
  C014: "일정 초안을 만드는 중에 문제가 생겼어요. 잠시 후 다시 만들어 주세요.",
};

export function getDraftFailureMessage(code: string | null): string {
  return (code === null ? undefined : DRAFT_FAILURE_MESSAGES[code]) ?? "일정 초안을 만들지 못했어요. 다시 시도해 주세요.";
}

const VERSION_MISMATCH_CODE = "C004";
const DRAFT_IN_PROGRESS_CODE = "SC003";
const DRAFT_NOT_FOUND_CODE = "SC001";

/** 그사이 여행방(정보·후보·일정)이나 초안이 바뀌어 요청이 거절됐는지(409 C004). */
export function isScheduleConflict(error: unknown): boolean {
  return isApiError(error) && error.code === VERSION_MISMATCH_CODE;
}

/** 같은 초안을 아직 계산하거나 저장하는 중이라 거절됐는지(422 SC003). */
export function isDraftInProgress(error: unknown): boolean {
  return isApiError(error) && error.code === DRAFT_IN_PROGRESS_CODE;
}

/** 초안이 없거나 보관 기간이 지났는지(404 SC001). */
export function isDraftNotFound(error: unknown): boolean {
  return isApiError(error) && error.code === DRAFT_NOT_FOUND_CODE;
}
