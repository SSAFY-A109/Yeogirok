import { api } from "@/api/client";
import { toPlaceSearchPage, type PlaceSearchPage, type SearchPlacesResponseDto } from "@/features/place/model";

export type SearchPlacesInput = { query: string; cursor: string | null; limit: number };

/**
 * API-026 여행지 검색. 다음 페이지가 없으면 `nextCursor`가 `null`이다.
 * 검색어 조건이 잘못되면 400 P002, 외부 장소 정보를 가져오지 못하면 502 P003이다.
 */
export async function searchPlaces({ query, cursor, limit }: SearchPlacesInput, signal?: AbortSignal): Promise<PlaceSearchPage> {
  const { data } = await api.get<SearchPlacesResponseDto>("/places", {
    params: { q: query, cursor: cursor ?? undefined, limit },
    signal,
  });
  return toPlaceSearchPage(data);
}
