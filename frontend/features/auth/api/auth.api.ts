import { api, TIMEOUT_MUTATION_MS } from "../../../api/client";
import { toLoginResult, toServiceTokens, type SocialLoginRequestDto } from "../model";

export async function exchangeSocialToken(credentials: SocialLoginRequestDto) {
  const { data } = await api.post<unknown>("/auth/token", credentials, {
    timeout: TIMEOUT_MUTATION_MS,
  });

  return toLoginResult(data);
}

export async function refreshServiceTokens(refreshToken: string) {
  const { data } = await api.post<unknown>("/auth/token/refresh", { refreshToken }, {
    timeout: TIMEOUT_MUTATION_MS,
  });

  return toServiceTokens(data);
}
