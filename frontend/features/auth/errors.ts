import { COMMON_ERROR_MESSAGES, isApiError } from "../../api/errors";

export class SocialLoginError extends Error {
  constructor(public readonly code: string) {
    super(code);
    this.name = "SocialLoginError";
  }
}

const messages: Readonly<Record<string, string>> = {
  A005: "소셜 계정을 확인하지 못했어요. 다시 로그인해 주세요.",
  A006: "로그인 확인이 만료됐어요. 다시 시도해 주세요.",
  AUTH_NOT_CONFIGURED: "소셜 로그인을 준비 중이에요. 잠시 후 다시 시도해 주세요.",
  AUTH_UNSUPPORTED: "소셜 로그인은 Android·iOS 앱에서 이용해 주세요.",
  AUTH_UNAVAILABLE: "소셜 로그인에 연결하지 못했어요. 연결 상태를 확인하고 다시 시도해 주세요.",
  GOOGLE_PLAY_SERVICES: "Google Play 서비스를 업데이트한 후 다시 시도해 주세요.",
};

export function getLoginErrorMessage(error: unknown): string | null {
  if (error == null) {
    return null;
  }

  if (error instanceof SocialLoginError) {
    return error.code === "AUTH_CANCELLED" ? null : messages[error.code] ?? messages.AUTH_UNAVAILABLE;
  }

  if (isApiError(error)) {
    return messages[error.code] ?? COMMON_ERROR_MESSAGES[error.code] ?? "로그인하지 못했어요. 다시 시도해 주세요.";
  }

  return "로그인하지 못했어요. 다시 시도해 주세요.";
}
