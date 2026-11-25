import { exchangeSocialToken } from "./api/auth.api";
import type { SocialProvider } from "./model";
import { getSocialCredentials } from "./native/social-login";
import { setLoginProvider, setTokens } from "./token-store";

/** 소셜 로그인 토큰을 발급한다. 기기 세션 저장 여부는 호출부가 결정한다. */
export async function requestSocialLogin(provider: SocialProvider) {
  const credentials = await getSocialCredentials(provider);
  const tokens = await exchangeSocialToken(credentials);
  return { tokens, serverAuthCode: credentials.serverAuthCode };
}

export async function performSocialLogin(provider: SocialProvider): Promise<void> {
  const { tokens } = await requestSocialLogin(provider);
  await setTokens(tokens);
  await setLoginProvider(provider);
}
