import { infiniteQueryOptions, useInfiniteQuery } from "@tanstack/react-query";

import { searchPlaces } from "@/features/place/api/place.api";

/** 검색 한 페이지의 장소 수. 허용 범위(C011)는 명세 미확정이라 기본값을 쓴다(TBD). */
const PLACE_SEARCH_PAGE_SIZE = 20;

export const placeKeys = {
  all: ["place"] as const,

  search: (keyword: string) => {
    return [...placeKeys.all, "search", keyword] as const;
  },
};

export function placeSearchOptions(keyword: string) {
  return infiniteQueryOptions({
    queryKey: placeKeys.search(keyword),

    queryFn: ({ pageParam, signal }) => {
      return searchPlaces({ keyword, cursor: pageParam, size: PLACE_SEARCH_PAGE_SIZE }, signal);
    },

    initialPageParam: null as string | null,

    getNextPageParam: (lastPage) => {
      return lastPage.nextCursor;
    },

    enabled: keyword.length > 0,
    staleTime: 60_000,
  });
}

/** 검색어(앞뒤 공백 제거)로 장소를 찾는다. 빈 검색어는 요청하지 않는다. */
export function useSearchPlaces(keyword: string) {
  return useInfiniteQuery(placeSearchOptions(keyword.trim()));
}
