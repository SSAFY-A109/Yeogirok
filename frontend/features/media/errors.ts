import { getErrorMessage, isApiError } from "@/api/errors";

const MEDIA_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  S3_UPLOAD_FAILED: "사진 파일을 전송하지 못했어요. 연결을 확인하고 다시 시도해 주세요.",
  C005: "이전 업로드 요청과 내용이 달라요. 파일을 다시 선택해 주세요.",
  M001: "사진은 파일당 20MB, 영수증은 파일당 10MB까지 업로드할 수 있어요.",
  M002: "JPEG·PNG·WEBP 파일을 선택해 주세요.",
  M003: "파일 검증에 실패했어요. 파일을 다시 선택해 주세요.",
  M004: "업로드 파일을 찾을 수 없어요.",
  M005: "이 파일을 사용할 권한이 없어요.",
  M006: "파일 전송을 확인하지 못했어요. 완료 확인을 다시 시도해 주세요.",
  M007: "파일 용도를 확인해 주세요.",
  M008: "이 기록에 파일을 올릴 권한이 없어요.",
  M011: "업로드 시간이 지났어요. 새 업로드를 시작해 주세요.",
  M012: "파일 저장소가 일시적으로 응답하지 않아요. 다시 시도해 주세요.",
  M013: "이미 완료한 업로드예요. 완료 상태를 다시 확인해 주세요.",
};

export function getMediaErrorMessage(error: unknown): string {
  if (isApiError(error) && error.status === 401) {
    return "로그인이 필요해요. 다시 로그인해 주세요.";
  }

  if (isApiError(error) && MEDIA_ERROR_MESSAGES[error.code]) {
    return MEDIA_ERROR_MESSAGES[error.code];
  }

  return getErrorMessage(error, {}, "파일을 업로드하지 못했어요. 다시 시도해 주세요.");
}
