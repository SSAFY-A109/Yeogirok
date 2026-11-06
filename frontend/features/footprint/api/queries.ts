import { mutationOptions, useMutation, useQueryClient } from "@tanstack/react-query";

import { createFootprint } from "@/features/footprint/api/footprint.api";
import { statisticsKeys } from "@/features/statistics/api/queries";

export const footprintKeys = {
  all: ["footprints"] as const,
};

/** 발자취 저장은 자동으로 다시 보내지 않는다. 사용자가 다시 시도할 때 같은 멱등키로 보낸다(규칙 90). */
const createFootprintOptions = mutationOptions({
  mutationFn: createFootprint,
  retry: false,
});

/** API-041. 저장하면 내 발자취와 방문 분포를 다시 받는다(api-guide §5). */
export function useCreateFootprint() {
  const queryClient = useQueryClient();

  return useMutation({
    ...createFootprintOptions,

    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: footprintKeys.all });
      void queryClient.invalidateQueries({ queryKey: statisticsKeys.all });
    },
  });
}
