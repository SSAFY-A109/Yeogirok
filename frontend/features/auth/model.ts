import { ApiError } from "../../api/errors";
import { isServiceTokenPair, type ServiceTokenPair } from "./tokens";

export type SocialProvider = "google" | "kakao";

export type SocialLoginRequestDto = {
  provider: SocialProvider;
  idToken: string;
  /** 로그인 1회마다 새로 만들어 SDK와 API-005에 같은 값으로 보낸다. */
  nonce: string;
};

/** 소셜 SDK 로그인 결과. `serverAuthCode`는 구글 탈퇴(API-009)에만 쓰는 1회용 코드라 저장하지 않는다. */
export type SocialCredentials = SocialLoginRequestDto & { serverAuthCode: string | null };

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
