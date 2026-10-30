import { isAxiosError } from "axios";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function isErrorEnvelope(
  value: unknown,
): value is { success: false; code: string; message: string } {
  return (
    typeof value === "object" && value !== null &&
    "success" in value && value.success === false &&
    "code" in value && typeof value.code === "string" &&
    "message" in value && typeof value.message === "string"
  );
}

export function toApiError(error: unknown): ApiError {
  if (isApiError(error)) return error;
  if (isAxiosError(error)) {
    const status = error.response?.status ?? 0;
    if (error.response) {
      return isErrorEnvelope(error.response.data)
        ? new ApiError(status, error.response.data.code, error.response.data.message)
        : new ApiError(status, "CONTRACT_MISMATCH", "오류 응답이 공통 봉투 형식과 다릅니다.");
    }
    if (error.code === "ECONNABORTED" || error.code === "ETIMEDOUT") {
      return new ApiError(0, "TIMEOUT", "API 요청 시간이 초과되었습니다.");
    }
    return new ApiError(0, "NETWORK", "API 서버에 연결할 수 없습니다.");
  }
  return new ApiError(0, "UNKNOWN", "API 요청을 처리하지 못했습니다.");
}

export const COMMON_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  C001: "요청을 처리할 수 없습니다. 다시 시도해 주세요.",
  C002: "요청을 처리할 수 없습니다. 다시 시도해 주세요.",
  C003: "입력한 값을 확인해 주세요.",
  C004: "로그인이 필요합니다.",
  C005: "로그인이 만료되었습니다. 다시 로그인해 주세요.",
  C006: "다시 로그인해 주세요.",
  C007: "접근 권한이 없습니다.",
  C008: "다른 일행이 내용을 변경했습니다. 최신 내용을 확인해 주세요.",
  C009: "요청을 처리할 수 없습니다. 다시 시도해 주세요.",
  C010: "목록을 처음부터 다시 불러와 주세요.",
  C011: "목록을 불러올 수 없습니다. 다시 시도해 주세요.",
  C012: "현재 위치를 다시 확인해 주세요.",
  C013: "위치 정보가 오래되었습니다. 다시 측정해 주세요.",
  C014: "탁 트인 곳에서 위치를 다시 측정해 주세요.",
  C015: "위치를 확인할 수 없습니다. 다시 시도해 주세요.",
  C016: "시작과 종료 날짜 또는 시간을 확인해 주세요.",
  C017: "지도 영역을 확인할 수 없습니다. 다시 시도해 주세요.",
  NETWORK: "인터넷 연결을 확인하고 다시 시도해 주세요.",
  TIMEOUT: "응답이 지연되고 있습니다. 다시 시도해 주세요.",
};
