import { getErrorMessage, isApiError } from "@/api/errors";

/** 여행방(RO)·장소(P) 도메인 code별 문구. 번호는 백엔드 `ExceptionType`(backend/dev, 2026-11-10)과 같다. */
const ROOM_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  RO001: "여행방을 찾을 수 없어요.",
  RO002: "참여 중인 여행방이 아니에요.",
  RO005: "초대 링크가 올바르지 않아요. 링크를 다시 받아 주세요.",
  RO007: "여행방 정원이 가득 찼어요. 누가 나가면 참여할 수 있어요.",
  RO009: "이미 빠진 장소예요. 목록을 다시 확인해 주세요.",
  RO010: "일정이 있는 여행방은 날짜를 비울 수 없어요.",
  P004: "장소 이름은 1~255자로 입력해 주세요.",
};

/** 다른 일행이 먼저 바꿔 version이 달라졌을 때(409 C004). 공통 문구보다 여행방 상황에 맞춘다. */
const ROOM_VERSION_MISMATCH_MESSAGE = "다른 일행이 여행방을 바꿨어요. 최신 내용으로 다시 시도해 주세요.";

/** 여행방이 없거나(삭제됨) 내가 참여자가 아니라는 code. 다시 시도해도 풀리지 않아 안내와 돌아가기만 보여 준다. */
export const ROOM_ACCESS_ERROR_CODES: readonly string[] = ["RO001", "RO002"];

/** 정원이 찼다는 code. 초대 수락 화면이 정원 안내로 분기한다. */
export const ROOM_FULL_CODE = "RO007";

/** 초대 토큰이 올바르지 않다는 code. */
export const ROOM_INVITE_INVALID_CODE = "RO005";

/** 날짜 범위·순서가 잘못됐다는 공통 code. 날짜 필드 아래에 보여 준다. */
export const ROOM_DATE_RANGE_CODE = "C012";

/** 일정이 있는 여행방의 날짜를 비우려 했다는 code. 날짜 필드 아래에 보여 준다. */
export const ROOM_DATES_IN_USE_CODE = "RO010";

export const ROOM_ERROR_FALLBACK = {
  load: "여행방 정보를 불러오지 못했어요.",
  save: "저장하지 못했어요. 다시 시도해 주세요.",
  leave: "여행방에서 나가지 못했어요. 다시 시도해 주세요.",
  invitation: "초대 링크를 만들지 못했어요.",
  join: "여행방에 참여하지 못했어요. 다시 시도해 주세요.",
  addPlaces: "장소를 담지 못했어요. 다시 시도해 주세요.",
  deletePlace: "장소를 빼지 못했어요. 다시 시도해 주세요.",
  restorePlace: "장소를 되돌리지 못했어요. 다시 시도해 주세요.",
  chat: "대화를 불러오지 못했어요.",
  chatHistory: "이전 대화를 불러오지 못했어요.",
} as const;

export function getRoomErrorMessage(error: unknown, fallback: string): string {
  if (isApiError(error) && error.status === 409 && error.code === "C004") {
    return ROOM_VERSION_MISMATCH_MESSAGE;
  }

  return getErrorMessage(error, ROOM_ERROR_MESSAGES, fallback);
}

const COURSE_IMPORT_ERRORS: Readonly<Record<string, string>> = {
  CO001: "이 코스는 더 이상 공개되어 있지 않아요.",
  CO006: "담을 장소를 다시 골라 주세요.",
  CO007: "코스 장소가 바뀌었어요. 코스에서 장소를 다시 골라 주세요.",
  RO001: "장소를 담을 여행방을 찾을 수 없어요.",
  RO002: "참여 중인 여행방에만 장소를 담을 수 있어요.",
};

export function getCourseImportErrorMessage(error: unknown): string {
  if (isApiError(error) && COURSE_IMPORT_ERRORS[error.code]) {
    return COURSE_IMPORT_ERRORS[error.code];
  }

  return "장소를 담았는지 확인할 수 없어요. 여행방의 장소 목록을 확인해 주세요.";
}
