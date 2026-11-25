import { getErrorMessage, isApiError } from "@/api/errors";
import { getMediaErrorMessage } from "@/features/media/errors";

// AR 명세의 C004~C010은 기존 도메인의 코드와 의미가 달라 이 요청 문맥에서 해석한다.
const TIP_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  A001: "로그인이 필요해요.",
  A002: "로그인 정보를 다시 확인해 주세요.",
  A003: "다시 로그인해 주세요.",
  A004: "이 기능을 사용할 권한이 없어요.",
  C001: "필수 내용을 입력해 주세요.",
  C002: "입력한 값의 형식을 확인해 주세요.",
  C003: "내용은 공백만 입력할 수 없고 최대 120자예요. 사진은 1장까지 첨부할 수 있어요.",
  C004: "팁이 변경됐어요. 최신 내용을 확인한 뒤 다시 수정해 주세요.",
  C005: "이전 요청과 내용이 달라요. 처리 상태를 확인한 뒤 다시 시도해 주세요.",
  C008: "현재 위치를 확인해 주세요.",
  C009: "위치 정보가 오래됐어요. 현재 위치를 다시 측정해 주세요.",
  C010: "위치가 정확하지 않아요. 탁 트인 곳에서 다시 측정해 주세요.",
  T001: "공개된 팁을 찾을 수 없어요.",
  T002: "본인이 작성한 팁만 관리할 수 있어요.",
  T004: "현재 위치가 팁에서 너무 멀어요. 팁이 있는 곳에 더 가까이 이동한 뒤 다시 시도해 주세요.",
  T005: "팁의 AR 공간 정보가 만료됐어요.",
  T006: "AR 공간 서비스가 일시적으로 응답하지 않아요. 잠시 후 다시 시도해 주세요.",
  T007: "AR 공간 정보를 확인하지 못했어요. 현장에서 위치를 다시 놓아 주세요.",
  T008: "재시도할 수정안을 찾을 수 없어요. 최신 팁에서 다시 수정해 주세요.",
  T009: "본인이 작성한 팁에는 반응할 수 없어요.",
  T010: "이 팁의 다른 수정 작업이 진행 중이에요. 완료 후 다시 시도해 주세요.",
  T012: "팁이 없거나 삭제됐어요.",
  M001: "팁 사진은 파일당 20MB까지 업로드할 수 있어요.",
  M004: "첨부할 파일을 찾을 수 없어요. 사진을 다시 선택해 주세요.",
  M005: "이 파일을 사용할 권한이 없어요.",
  M006: "사진 검증이 아직 끝나지 않았어요. 완료 확인 후 다시 시도해 주세요.",
  M007: "팁용으로 업로드한 사진을 선택해 주세요.",
};

export function isTipUnavailable(error: unknown) {
  return isApiError(error) && (error.status === 403 || error.status === 404);
}

export function getTipErrorMessage(error: unknown): string {
  if (isApiError(error) && error.status === 401) {
    return "로그인이 필요해요. 다시 로그인해 주세요.";
  }

  if (isApiError(error) && TIP_ERROR_MESSAGES[error.code]) {
    return TIP_ERROR_MESSAGES[error.code];
  }

  if (isApiError(error) && (error.code.startsWith("M") || error.code === "S3_UPLOAD_FAILED")) {
    return getMediaErrorMessage(error);
  }

  return getErrorMessage(error, {}, "팁 요청을 처리하지 못했어요. 다시 시도해 주세요.");
}
