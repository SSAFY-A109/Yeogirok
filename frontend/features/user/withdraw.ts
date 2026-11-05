import type { SocialProvider } from "../auth/model";
import { signInAndStoreTokens } from "../auth/sign-in";
import { clearTokens } from "../auth/token-store";
import { deleteMe } from "./api/user.api";

/**
 * 같은 제공자로 다시 로그인(API-005) → 방금 받은 토큰으로 탈퇴(API-009) → 기기 토큰 삭제.
 * 제공자 연결 해제는 서버가 하므로 SDK의 unlink·revokeAccess는 부르지 않는다.
 * 재로그인 뒤 탈퇴가 실패하면 새 토큰으로 로그인된 상태가 유지된다.
 */
export async function withdrawWithReauthentication(provider: SocialProvider): Promise<void> {
  const { serverAuthCode } = await signInAndStoreTokens(provider);
  await deleteMe(provider === "google" ? serverAuthCode : null);
  await clearTokens();
}
