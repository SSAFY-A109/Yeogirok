import type { SocialCredentials, SocialProvider } from "../../model";
import { createNonce } from "../../nonce";

export async function getSocialCredentials(provider: SocialProvider): Promise<SocialCredentials> {
  if (__DEV__) {
    const { mockSocialCredentials } = await import("../../../../api/mocks/fixtures/auth");
    return mockSocialCredentials(provider);
  }

  const nonce = createNonce();

  if (provider === "google") {
    const { signInWithGoogle } = await import("./google");
    return signInWithGoogle(nonce);
  }

  const { signInWithKakao } = await import("./kakao");
  return signInWithKakao(nonce);
}
