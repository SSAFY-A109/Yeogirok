import { initializeKakaoSDK } from "@react-native-kakao/core";
import { login } from "@react-native-kakao/user";

import { SocialLoginError } from "../../errors";
import type { SocialLoginRequestDto } from "../../model";

export async function signInWithKakao(): Promise<SocialLoginRequestDto> {
  const nativeAppKey = process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY;

  if (!nativeAppKey) {
    throw new SocialLoginError("AUTH_NOT_CONFIGURED");
  }

  try {
    await initializeKakaoSDK(nativeAppKey);
    const token = await login();

    if (!token.idToken) {
      throw new SocialLoginError("AUTH_NOT_CONFIGURED");
    }

    return { provider: "kakao", idToken: token.idToken };
  } catch (error) {
    if (error instanceof SocialLoginError) {
      throw error;
    }

    if (typeof error === "object" && error !== null && "code" in error && typeof error.code === "string" &&
      ["cancelled", "accessdenied", "access_denied"].includes(error.code.toLowerCase())) {
      throw new SocialLoginError("AUTH_CANCELLED");
    }

    throw new SocialLoginError("AUTH_UNAVAILABLE");
  }
}
