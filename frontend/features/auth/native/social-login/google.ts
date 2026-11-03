import { GoogleSignin, isErrorWithCode, isSuccessResponse, statusCodes } from "@react-native-google-signin/google-signin";
import { Platform } from "react-native";

import { SocialLoginError } from "../../errors";
import type { SocialLoginRequestDto } from "../../model";

export async function signInWithGoogle(): Promise<SocialLoginRequestDto> {
  const webClientId = process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID;
  const iosClientId = process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID;

  if (!webClientId || (Platform.OS === "ios" && !iosClientId)) {
    throw new SocialLoginError("AUTH_NOT_CONFIGURED");
  }

  try {
    GoogleSignin.configure({ webClientId, iosClientId });

    if (Platform.OS === "android") {
      await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
    }

    const response = await GoogleSignin.signIn();

    if (!isSuccessResponse(response)) {
      throw new SocialLoginError("AUTH_CANCELLED");
    }

    if (!response.data.idToken) {
      throw new SocialLoginError("AUTH_NOT_CONFIGURED");
    }

    return { provider: "google", idToken: response.data.idToken };
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
