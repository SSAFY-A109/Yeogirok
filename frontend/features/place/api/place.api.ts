import { api } from "@/api/client";
import { toPlaceSearchPage, type PlaceSearchPage, type SearchPlacesResponseDto } from "@/features/place/model";

export type SearchPlacesInput = { keyword: string; cursor: string | null; size: number };

/** API-026 여행지 검색. 다음 페이지가 없으면 `nextCursor`가 `null`이다. */
export async function searchPlaces({ keyword, cursor, size }: SearchPlacesInput, signal?: AbortSignal): Promise<PlaceSearchPage> {
  const { data } = await api.get<SearchPlacesResponseDto>("/places", {
    params: { keyword, cursor: cursor ?? undefined, size },
    signal,
  });
  return toPlaceSearchPage(data);
}
