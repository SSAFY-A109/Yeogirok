import { SocialLoginError } from "../../errors";
import type { SocialCredentials, SocialProvider } from "../../model";

export async function getSocialCredentials(provider: SocialProvider): Promise<SocialCredentials> {
  if (__DEV__) {
    const { mockSocialCredentials } = await import("../../../../api/mocks/fixtures/auth.js");
    return mockSocialCredentials(provider);
  }

  throw new SocialLoginError("AUTH_UNSUPPORTED");
}
