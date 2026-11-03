import type { SocialLoginRequestDto, SocialProvider } from "../../model";

export async function getSocialCredentials(provider: SocialProvider): Promise<SocialLoginRequestDto> {
  if (__DEV__) {
    const { socialCredentialsFixture } = await import("../../../../api/mocks/fixtures/auth");
    return socialCredentialsFixture[provider];
  }

  if (provider === "google") {
    const { signInWithGoogle } = await import("./google");
    return signInWithGoogle();
  }

  const { signInWithKakao } = await import("./kakao");
  return signInWithKakao();
}
