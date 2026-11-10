import { infiniteQueryOptions, useInfiniteQuery } from "@tanstack/react-query";

import { searchPlaces } from "@/features/place/api/place.api";

/** 검색 한 페이지의 장소 수(`limit`). 허용 범위(C007)는 명세 상세에 없어 기본값을 쓴다(TBD). */
const PLACE_SEARCH_PAGE_SIZE = 20;

export const placeKeys = {
  all: ["place"] as const,

  search: (query: string) => {
    return [...placeKeys.all, "search", query] as const;
  },
};

export function placeSearchOptions(query: string) {
  return infiniteQueryOptions({
    queryKey: placeKeys.search(query),

    queryFn: ({ pageParam, signal }) => {
      return searchPlaces({ query, cursor: pageParam, limit: PLACE_SEARCH_PAGE_SIZE }, signal);
    },

    initialPageParam: null as string | null,

    getNextPageParam: (lastPage) => {
      return lastPage.nextCursor;
    },

    enabled: query.length > 0,
    staleTime: 60_000,
  });
}

/** 검색어(앞뒤 공백 제거)로 장소를 찾는다. 빈 검색어는 요청하지 않는다. */
export function useSearchPlaces(query: string) {
  return useInfiniteQuery(placeSearchOptions(query.trim()));
}
