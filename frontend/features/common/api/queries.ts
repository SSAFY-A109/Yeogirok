import { queryOptions, useQuery } from "@tanstack/react-query";

import { getCategories, getRegions } from "@/features/common/api/common.api";

/** 기준 데이터는 자주 바뀌지 않으므로 한 시간 동안 다시 받지 않는다. */
const REFERENCE_DATA_STALE_MS = 60 * 60 * 1000;

export const commonKeys = {
  all: ["common"] as const,

  categories: () => {
    return [...commonKeys.all, "categories"] as const;
  },

  regions: () => {
    return [...commonKeys.all, "regions"] as const;
  },
};

export function categoriesOptions() {
  return queryOptions({
    queryKey: commonKeys.categories(),

    queryFn: ({ signal }) => {
      return getCategories(signal);
    },

    staleTime: REFERENCE_DATA_STALE_MS,
  });
}

export function regionsOptions() {
  return queryOptions({
    queryKey: commonKeys.regions(),

    queryFn: ({ signal }) => {
      return getRegions(signal);
    },

    staleTime: REFERENCE_DATA_STALE_MS,
  });
}

export function useCategories() {
  return useQuery(categoriesOptions());
}

export function useRegions() {
  return useQuery(regionsOptions());
}
