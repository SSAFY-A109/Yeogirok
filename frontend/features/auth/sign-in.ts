import { exchangeSocialToken } from "./api/auth.api";
import type { SocialProvider } from "./model";
import { getSocialCredentials } from "./native/social-login";
import { setTokens } from "./token-store";

/**
 * 소셜 SDK 로그인 → API-005 → 토큰 저장까지 하고, 구글 탈퇴에 쓸 1회용 코드를 돌려준다.
 * 탈퇴(API-009)는 로그인 직후 발급된 토큰만 받으므로 탈퇴 전 재인증에도 이 함수를 쓴다.
 */
export async function signInAndStoreTokens(provider: SocialProvider): Promise<{ serverAuthCode: string | null }> {
  const credentials = await getSocialCredentials(provider);
  const tokens = await exchangeSocialToken(credentials);
  await setTokens(tokens);

  return { serverAuthCode: credentials.serverAuthCode };
}

export async function performSocialLogin(provider: SocialProvider): Promise<void> {
  await signInAndStoreTokens(provider);
}
