import { ApiError } from "../../api/errors";
import type { SocialProvider } from "../auth/model";
import { requestSocialLogin } from "../auth/sign-in";
import { clearTokens, getTokens } from "../auth/token-store";
import { deleteMe, getMe } from "./api/user.api";

/**
 * 현재 계정과 재인증 계정이 일치하면 재인증 토큰으로 탈퇴한다.
 * 제공자 연결 해제는 서버가 하므로 SDK의 unlink·revokeAccess는 부르지 않는다.
 */
export async function withdrawWithReauthentication(provider: SocialProvider): Promise<void> {
  const session = await getTokens();

  if (!session) {
    throw new ApiError(401, "A001", "탈퇴할 로그인 세션이 없습니다.");
  }

  const account = await getMe(undefined, session.accessToken);
  const { tokens: { accessToken }, serverAuthCode } = await requestSocialLogin(provider);
  const reauthenticatedAccount = await getMe(undefined, accessToken);

  if (account.id !== reauthenticatedAccount.id) {
    throw new ApiError(0, "WITHDRAW_ACCOUNT_MISMATCH", "재인증 계정이 현재 계정과 다릅니다.");
  }

  await deleteMe(provider === "google" ? serverAuthCode : null, accessToken);
  await clearTokens();
}
