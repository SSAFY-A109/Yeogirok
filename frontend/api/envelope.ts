import { ApiError, isErrorEnvelope } from "./errors";

export function unwrapEnvelope(value: unknown, status: number): unknown {
  if (isErrorEnvelope(value)) {
    throw new ApiError(status, value.code, value.message);
  }
  if (
    typeof value !== "object" || value === null ||
    !("success" in value) || value.success !== true || !("data" in value)
  ) {
    throw new ApiError(status, "CONTRACT_MISMATCH", "성공 응답이 공통 봉투 형식과 다릅니다.");
  }
  return value.data === null ? undefined : value.data;
}
