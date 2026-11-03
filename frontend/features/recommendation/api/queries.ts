import { queryOptions, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  getRecommendationPreference,
  recommendPlaces,
  saveRecommendationPreference,
} from "@/features/recommendation/api/recommendation.api";
import type { RecommendationPreferenceDto } from "@/features/recommendation/model";

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

/**
 * 조건을 저장한 뒤 추천을 받는다. 추천 결과는 자동으로 담기지 않으며, 결과는 뮤테이션의 `data`로 쓴다.
 * 담기는 `useAddRoomPlaces`(source: AI_RECOMMENDATION)로 한다.
 */
export function useRecommendPlaces(roomId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (request: RecommendationPreferenceDto) => {
      await saveRecommendationPreference({ roomId, request });
      return recommendPlaces(roomId);
    },

    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: recommendationKeys.preference(roomId) });
    },
  });
}
