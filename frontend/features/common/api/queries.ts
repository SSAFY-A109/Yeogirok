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

  districts: (provinceCode: string) => {
    return [...commonKeys.regions(), provinceCode] as const;
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

/** 시·도 목록(API-078). */
export function regionsOptions() {
  return queryOptions({
    queryKey: commonKeys.regions(),

    queryFn: ({ signal }) => {
      return getRegions(null, signal);
    },

    staleTime: REFERENCE_DATA_STALE_MS,
  });
}

/** 시·도 하나의 시·군·구 목록(API-078 `parentCode`). */
export function districtsOptions(provinceCode: string) {
  return queryOptions({
    queryKey: commonKeys.districts(provinceCode),

    queryFn: ({ signal }) => {
      return getRegions(provinceCode, signal);
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

/** 고른 시·도의 시·군·구. 시·도를 고르기 전(`null`)에는 부르지 않는다. */
export function useDistricts(provinceCode: string | null) {
  return useQuery({ ...districtsOptions(provinceCode ?? ""), enabled: provinceCode !== null });
}
