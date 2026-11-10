import { api, idempotentRequestConfig, TIMEOUT_MUTATION_MS } from "@/api/client";
import {
  toPlaceRecommendations,
  toRecommendationPreference,
  toSaveRecommendationPreferenceRequest,
  type PlaceRecommendations,
  type PlaceRecommendationsDto,
  type RecommendationConditions,
  type RecommendationPreference,
  type RecommendationPreferenceDto,
  type RecommendPlacesRequestDto,
} from "@/features/recommendation/model";

/** API-081 여행방 추천 조건. 저장한 적이 없으면 `null`(TBD, 상세 미확인). */
export async function getRecommendationPreference(roomId: number, signal?: AbortSignal): Promise<RecommendationPreference | null> {
  const { data } = await api.get<RecommendationPreferenceDto | undefined>(`/rooms/${roomId}/preferences`, { signal });
  return data === undefined ? null : toRecommendationPreference(data);
}

export type SaveRecommendationPreferenceInput = { roomId: number; conditions: RecommendationConditions; version: number };

/** API-033 추천 조건 저장. 저장된 조건과 다음 저장에 보낼 version을 돌려준다. */
export async function saveRecommendationPreference({
  roomId,
  conditions,
  version,
}: SaveRecommendationPreferenceInput): Promise<RecommendationPreference> {
  const request = toSaveRecommendationPreferenceRequest(conditions, version);
  const { data } = await api.put<RecommendationPreferenceDto>(`/rooms/${roomId}/preferences`, request, { timeout: TIMEOUT_MUTATION_MS });
  return toRecommendationPreference(data);
}

export type RecommendPlacesInput = { roomId: number; idempotencyKey: string };

/** API-034 여행지 추천. 서버가 저장된 조건(API-033)으로 추천한다. 선택 항목(`limit`·`excludedPlaces`)은 보내지 않는다. */
export async function recommendPlaces({ roomId, idempotencyKey }: RecommendPlacesInput): Promise<PlaceRecommendations> {
  const request: RecommendPlacesRequestDto = {};

  const { data } = await api.post<PlaceRecommendationsDto>(
    `/rooms/${roomId}/place-recommendations`,
    request,
    idempotentRequestConfig(idempotencyKey),
  );

  return toPlaceRecommendations(data);
}
