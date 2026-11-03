import { api, TIMEOUT_MUTATION_MS } from "@/api/client";
import {
  toPlaceRecommendations,
  toRecommendationPreference,
  type PlaceRecommendations,
  type PlaceRecommendationsDto,
  type RecommendationPreference,
  type RecommendationPreferenceDto,
} from "@/features/recommendation/model";

/** API-081 여행방 추천 조건. 저장한 적이 없으면 `null`. */
export async function getRecommendationPreference(roomId: number, signal?: AbortSignal): Promise<RecommendationPreference | null> {
  const { data } = await api.get<RecommendationPreferenceDto | undefined>(`/rooms/${roomId}/preferences`, { signal });
  return data === undefined ? null : toRecommendationPreference(data);
}

export type SaveRecommendationPreferenceInput = { roomId: number; request: RecommendationPreferenceDto };

/** API-033 추천 조건 저장. */
export async function saveRecommendationPreference({ roomId, request }: SaveRecommendationPreferenceInput): Promise<void> {
  await api.put<void>(`/rooms/${roomId}/preferences`, request, { timeout: TIMEOUT_MUTATION_MS });
}

/** API-034 여행지 추천. 저장한 조건과 담은 장소를 기준으로 서버가 추천한다. */
export async function recommendPlaces(roomId: number): Promise<PlaceRecommendations> {
  const { data } = await api.post<PlaceRecommendationsDto>(`/rooms/${roomId}/place-recommendations`, null, {
    timeout: TIMEOUT_MUTATION_MS,
  });
  return toPlaceRecommendations(data);
}
