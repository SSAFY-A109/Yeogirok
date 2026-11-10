import { getErrorMessage, isApiError } from "@/api/errors";

/** 여행방(RO) 도메인 code별 문구. 도메인 code는 명세 미확정이라 아직 없다(TBD). */
const ROOM_ERROR_MESSAGES: Readonly<Record<string, string>> = {};

export const ROOM_ERROR_FALLBACK = {
  load: "여행방 정보를 불러오지 못했어요.",
  save: "저장하지 못했어요. 다시 시도해 주세요.",
  leave: "여행방에서 나가지 못했어요. 다시 시도해 주세요.",
  invitation: "초대 링크를 만들지 못했어요.",
  addPlaces: "장소를 담지 못했어요. 다시 시도해 주세요.",
  deletePlace: "장소를 빼지 못했어요. 다시 시도해 주세요.",
} as const;

export function getRoomErrorMessage(error: unknown, fallback: string): string {
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
