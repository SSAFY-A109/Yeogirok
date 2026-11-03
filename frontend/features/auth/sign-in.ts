import { exchangeSocialToken } from "./api/auth.api";
import type { SocialProvider } from "./model";
import { getSocialCredentials } from "./native/social-login";
import { setTokens } from "./token-store";

export async function performSocialLogin(provider: SocialProvider): Promise<void> {
  const credentials = await getSocialCredentials(provider);
  const tokens = await exchangeSocialToken(credentials);
  await setTokens(tokens);
}
