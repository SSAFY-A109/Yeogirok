import { runSessionLogout } from "../../api/client";
import { getChatPushToken } from "../../lib/native/chat-notifications";
import { revokeRefreshToken } from "./api/auth.api";
import { clearTokens, getTokens } from "./token-store";

/** 서버에서 이 기기의 리프레시 토큰을 폐기한 뒤 기기 토큰을 지운다. */
export function signOutThisDevice(): Promise<void> {
  return runSessionLogout(async () => {
    const tokens = await getTokens();

    if (tokens) {
      await revokeRefreshToken(tokens.refreshToken, getChatPushToken() ?? undefined);
    }

    await clearTokens();
  });
}
