import { queryOptions, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  getRecommendationPreference,
  recommendPlaces,
  saveRecommendationPreference,
} from "@/features/recommendation/api/recommendation.api";
import { INITIAL_PREFERENCE_VERSION, type RecommendationConditions } from "@/features/recommendation/model";

export const recommendationKeys = {
  all: ["recommendation"] as const,

  preference: (roomId: number) => {
    return [...recommendationKeys.all, "preference", roomId] as const;
  },
};

export function recommendationPreferenceOptions(roomId: number) {
  return queryOptions({
    queryKey: recommendationKeys.preference(roomId),

    queryFn: ({ signal }) => {
      return getRecommendationPreference(roomId, signal);
    },
  });
}

export function useRecommendationPreference(roomId: number) {
  return useQuery(recommendationPreferenceOptions(roomId));
}

export type RecommendPlacesVariables = { conditions: RecommendationConditions; idempotencyKey: string };

/**
 * 조건을 저장한 뒤(API-033) 추천을 받는다(API-034). 추천 결과는 자동으로 담기지 않으며, 결과는 뮤테이션의 `data`로 쓴다.
 * 저장 응답을 조건 캐시에 적어 두어, 추천이 실패해 다시 시도할 때도 새 version을 보낸다.
 * 담기는 `useAddRoomPlaces`(sourceType: AI)로 한다.
 */
export function useRecommendPlaces(roomId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ conditions, idempotencyKey }: RecommendPlacesVariables) => {
      const preferenceKey = recommendationPreferenceOptions(roomId).queryKey;
      const version = queryClient.getQueryData(preferenceKey)?.version ?? INITIAL_PREFERENCE_VERSION;
      const savedPreference = await saveRecommendationPreference({ roomId, conditions, version });
      queryClient.setQueryData(preferenceKey, savedPreference);
      return recommendPlaces({ roomId, idempotencyKey });
    },

    retry: false,

    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: recommendationKeys.preference(roomId) });
    },
  });
}
