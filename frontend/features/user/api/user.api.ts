import { api, TIMEOUT_MUTATION_MS } from "../../../api/client";
import { toMe, toWithdrawResult, type Me, type WithdrawRequestDto, type WithdrawResponseDto } from "../model";

/** API-008 내 정보 조회. */
export async function getMe(signal?: AbortSignal, accountAccessToken?: string): Promise<Me> {
  const { data } = await api.get<unknown>("/users/me", { signal, accountAccessToken });
  return toMe(data);
}

/**
 * API-009 회원 탈퇴. 로그인(API-005) 후 5분 안에 발급된 액세스 토큰으로만 성공한다.
 * 구글 회원은 재로그인에서 받은 1회용 serverAuthCode를 바로 보내야 한다.
 */
export async function deleteMe(googleAuthCode: string | null, accountAccessToken?: string): Promise<WithdrawResponseDto> {
  const body: WithdrawRequestDto | undefined = googleAuthCode === null ? undefined : { googleAuthCode };
  const { data } = await api.delete<unknown>("/users/me", { data: body, accountAccessToken, timeout: TIMEOUT_MUTATION_MS });
  return toWithdrawResult(data);
}
