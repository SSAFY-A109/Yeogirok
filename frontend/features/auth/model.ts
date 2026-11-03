import { ApiError } from "../../api/errors";
import { isServiceTokenPair, type ServiceTokenPair } from "./tokens";

export type SocialProvider = "google" | "kakao";

export type SocialLoginRequestDto = {
  provider: SocialProvider;
  idToken: string;
  nonce?: string;
};

export type SocialLoginResponseDto = ServiceTokenPair & { isNewUser: boolean };

export function toServiceTokens(value: unknown): ServiceTokenPair {
  if (!isServiceTokenPair(value)) {
    throw new ApiError(200, "CONTRACT_MISMATCH", "서비스 토큰 응답이 현재 계약과 다릅니다.");
  }

  return value;
}

export function toLoginResult(value: unknown): SocialLoginResponseDto {
  const tokens = toServiceTokens(value);

  if (!("isNewUser" in tokens) || typeof tokens.isNewUser !== "boolean") {
    throw new ApiError(200, "CONTRACT_MISMATCH", "로그인 응답에 가입 여부가 없습니다.");
  }

  return { ...tokens, isNewUser: tokens.isNewUser };
}
