import { SocialLoginError } from "../../errors";
import type { SocialLoginRequestDto, SocialProvider } from "../../model";

export async function getSocialCredentials(provider: SocialProvider): Promise<SocialLoginRequestDto> {
  if (__DEV__) {
    const { socialCredentialsFixture } = await import("../../../../api/mocks/fixtures/auth.js");
    return socialCredentialsFixture[provider];
  }

  throw new SocialLoginError("AUTH_UNSUPPORTED");
}
