import { GoogleOneTapSignIn, isCancelledResponse, isErrorWithCode, isSuccessResponse, statusCodes } from "react-native-nitro-google-signin";

import { SocialLoginError } from "../../errors";
import type { SocialCredentials } from "../../model";

// 로그인 버튼과 탈퇴 전 재로그인은 기기의 모든 구글 계정을 보여 주는 presentExplicitSignIn을 쓴다.
// signIn()은 이 앱에 로그인한 적 있는 계정만 보여 줘서 처음 쓰는 계정을 고를 수 없다.
export async function signInWithGoogle(nonce: string): Promise<SocialCredentials> {
  const webClientId = process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID;

  if (!webClientId) {
    throw new SocialLoginError("AUTH_NOT_CONFIGURED");
  }

  try {
    GoogleOneTapSignIn.configure({ webClientId, nonce, offlineAccess: true });
    await GoogleOneTapSignIn.checkPlayServices(true);

    const response = await GoogleOneTapSignIn.presentExplicitSignIn();

    if (isCancelledResponse(response)) {
      throw new SocialLoginError("AUTH_CANCELLED");
    }

    if (!isSuccessResponse(response) || !response.data.idToken) {
      throw new SocialLoginError("AUTH_NOT_CONFIGURED");
    }

    return { provider: "google", idToken: response.data.idToken, nonce, serverAuthCode: response.data.serverAuthCode };
  } catch (error) {
    if (error instanceof SocialLoginError) {
      throw error;
    }

    if (isErrorWithCode(error)) {
      if (error.code === statusCodes.SIGN_IN_CANCELLED) {
        throw new SocialLoginError("AUTH_CANCELLED");
      }

      if (error.code === statusCodes.PLAY_SERVICES_NOT_AVAILABLE) {
        throw new SocialLoginError("GOOGLE_PLAY_SERVICES");
      }
    }

    throw new SocialLoginError("AUTH_UNAVAILABLE");
  }
}
