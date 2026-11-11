import { api, TIMEOUT_MUTATION_MS } from "../../../api/client";
import { toLoginResult, toServiceTokens, type SocialLoginRequestDto } from "../model";

/** API-005 로그인. SDK에 넘긴 nonce를 해시하지 않고 그대로 보낸다. */
export async function exchangeSocialToken({ provider, idToken, nonce }: SocialLoginRequestDto) {
  const { data } = await api.post<unknown>("/auth/token", { provider, idToken, nonce }, {
    timeout: TIMEOUT_MUTATION_MS,
  });

  return toLoginResult(data);
}

/** API-006 재발급. 앱의 401 자동 갱신은 `api/client.ts`가 처리한다. */
export async function refreshServiceTokens(refreshToken: string) {
  const { data } = await api.post<unknown>("/auth/token/refresh", { refreshToken }, {
    timeout: TIMEOUT_MUTATION_MS,
  });

  return toServiceTokens(data);
}

/** API-007 로그아웃. 이 기기의 리프레시 토큰만 폐기하고, 여러 번 호출해도 성공한다. */
export async function revokeRefreshToken(refreshToken: string, pushToken?: string | null): Promise<void> {
  await api.post<void>("/auth/logout", { refreshToken, pushToken }, { timeout: TIMEOUT_MUTATION_MS });
}
