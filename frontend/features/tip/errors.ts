import { getErrorMessage, isApiError } from "@/api/errors";
import { getMediaErrorMessage } from "@/features/media/errors";

const TIP_MESSAGES: Readonly<Record<string, string>> = {
  C001: "필수 내용을 입력해 주세요.",
  C002: "입력한 값의 형식을 확인해 주세요.",
  C003: "팁 내용은 공백을 포함해 120자까지 쓸 수 있고 사진은 1장까지 첨부할 수 있어요.",
  C004: "팁이 변경됐어요. 최신 내용을 확인한 뒤 다시 수정해 주세요.",
  C005: "이전 수정 요청과 내용이 달라요. 게시·수정 상태를 확인해 주세요.",
  T002: "본인이 작성한 팁만 관리할 수 있어요.",
  T010: "다른 수정 작업이 진행 중이에요. 완료 후 다시 시도해 주세요.",
  T012: "팁이 없거나 삭제됐어요.",
};

export function isTipUnavailable(error: unknown) {
  return isApiError(error) && (error.status === 403 || error.status === 404);
}

export function getTipErrorMessage(error: unknown): string {
  if (isApiError(error) && (error.code.startsWith("M") || error.code === "S3_UPLOAD_FAILED")) {
    return getMediaErrorMessage(error);
  }

  return isApiError(error) ? TIP_MESSAGES[error.code] ?? getErrorMessage(error, {}, "다시 시도해 주세요.") : "팁을 처리하지 못했어요. 다시 시도해 주세요.";
}
