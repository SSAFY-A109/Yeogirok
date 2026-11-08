import { infiniteQueryOptions, mutationOptions, queryOptions, useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { userKeys } from "@/features/user/api/queries";
import { deleteFootprint, getFootprint, getFootprints, updateGuestbook } from "./footprint.api";
import type { FootprintFilters } from "../model";

export const footprintKeys = {
  lists: ["footprint", "list"] as const,

  list: (filters: FootprintFilters) => {
    return [...footprintKeys.lists, filters] as const;
  },

  detail: (id: number) => {
    return ["footprint", "detail", id] as const;
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
      void client.invalidateQueries({ queryKey: userKeys.me() });
    },
  });
}
