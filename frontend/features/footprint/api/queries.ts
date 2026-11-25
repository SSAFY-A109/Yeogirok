import { infiniteQueryOptions, mutationOptions, queryOptions, useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { statisticsKeys } from "@/features/statistics/api/queries";
import { userKeys } from "@/features/user/api/queries";
import { createFootprint, deleteFootprint, getFootprint, getFootprints, updateGuestbook } from "./footprint.api";
import type { FootprintFilters } from "../model";

const FOOTPRINT_KEY = ["footprints"] as const;

export const footprintKeys = {
  all: FOOTPRINT_KEY,
  lists: [...FOOTPRINT_KEY, "list"] as const,

  list: (filters: FootprintFilters) => {
    return [...footprintKeys.lists, filters] as const;
  },

  detail: (id: number) => {
    return [...footprintKeys.all, "detail", id] as const;
  },
};

function footprintsOptions(filters: FootprintFilters) {
  return infiniteQueryOptions({
    queryKey: footprintKeys.list(filters),
    initialPageParam: null as string | null,

    queryFn: ({ pageParam, signal }) => {
      return getFootprints({ ...filters, cursor: pageParam ?? undefined }, signal);
    },

    getNextPageParam: (page) => {
      return page.nextCursor;
    },
  });
}

function footprintOptions(id: number) {
  return queryOptions({
    queryKey: footprintKeys.detail(id),

    queryFn: ({ signal }) => {
      return getFootprint(id, signal);
    },
  });
}

const updateGuestbookOptions = mutationOptions({ mutationFn: updateGuestbook });
const deleteFootprintOptions = mutationOptions({ mutationFn: deleteFootprint });

export function useFootprints(filters: FootprintFilters) {
  return useInfiniteQuery(footprintsOptions(filters));
}

export function useFootprint(id: number) {
  return useQuery(footprintOptions(id));
}

export function useUpdateGuestbook() {
  const client = useQueryClient();
  return useMutation({
    ...updateGuestbookOptions,

    onSuccess: (_result, { id }) => {
      void client.invalidateQueries({ queryKey: footprintKeys.detail(id) });
      void client.invalidateQueries({ queryKey: footprintKeys.lists });
    },
  });
}

export function useDeleteFootprint() {
  const client = useQueryClient();
  return useMutation({
    ...deleteFootprintOptions,

    onSuccess: (_result, { id }) => {
      client.removeQueries({ queryKey: footprintKeys.detail(id) });
      void client.invalidateQueries({ queryKey: footprintKeys.lists });
      void client.invalidateQueries({ queryKey: statisticsKeys.all });
      void client.invalidateQueries({ queryKey: userKeys.me() });
    },
  });
}

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
      void queryClient.invalidateQueries({ queryKey: userKeys.me() });
    },
  });
}
