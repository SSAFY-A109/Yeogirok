import { revokeRefreshToken } from "./api/auth.api";
import { clearTokens, getTokens } from "./token-store";

/** 서버에 이 기기의 리프레시 토큰 폐기를 요청하고, 요청이 실패해도 기기 토큰은 지운다. */
export async function signOutThisDevice(): Promise<void> {
  const tokens = await getTokens();

  try {
    if (tokens) {
      await revokeRefreshToken(tokens.refreshToken);
    }
  } finally {
    await clearTokens();
  }
}
