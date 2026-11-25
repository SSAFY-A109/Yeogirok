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

/** 서버가 받는 푸시 플랫폼. 허용값은 Swagger에 JsonNode로만 나와 미확인(TBD)이며, FCM을 쓰는 Android만 등록한다. */
export type PushPlatform = "ANDROID";

/** API-095 푸시 토큰 등록. 같은 토큰을 다시 보내도 성공하고, 다른 계정의 토큰이면 지금 계정으로 옮긴다. */
export async function registerPushToken(token: string, platform: PushPlatform): Promise<void> {
  await api.put<void>("/users/me/push-tokens", { token, platform }, { timeout: TIMEOUT_MUTATION_MS });
}

/** API-096 푸시 토큰 삭제. 기기 토큰이 바뀌었을 때 이전 토큰을 지운다. 등록된 토큰이 없어도 성공한다. */
export async function deletePushToken(token: string): Promise<void> {
  await api.delete<void>("/users/me/push-tokens", { data: { token }, timeout: TIMEOUT_MUTATION_MS });
}
