import { delay, http } from "msw";

import type { PlaceDto, SearchPlacesResponseDto } from "../../../features/place/model";
import type { RoomPlaceInfoDto } from "../../../features/room/model";

import { placeCatalog } from "../fixtures/place";
import { fail, MOCK_DELAY_MS, ok } from "./respond";

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 50;

/** 검색(API-026)·추천(API-034) 응답의 장소 모양. 카테고리 이름과 지역은 주지 않는다. */
export function toPlaceDto(place: RoomPlaceInfoDto): PlaceDto {
  return {
    provider: place.provider,
    providerPlaceId: place.providerPlaceId,
    name: place.name,
    latitude: place.latitude,
    longitude: place.longitude,
    address: place.address,
    categoryCode: place.categoryCode,
    imageUrl: place.imageUrl,
    imageAttribution: place.imageAttribution,
  };
}

export const placeHandlers = [
  http.get("*/api/v1/places", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const url = new URL(request.url);
    const query = url.searchParams.get("q")?.trim() ?? "";
    const regionCode = url.searchParams.get("regionCode");
    const limit = Number(url.searchParams.get("limit") ?? DEFAULT_PAGE_SIZE);
    const offset = Number(url.searchParams.get("cursor") ?? 0);

    if (query === "") {
      return fail(400, "P002", "장소 검색어 조건이 올바르지 않습니다.");
    }

    if (!Number.isInteger(limit) || limit < 1 || limit > MAX_PAGE_SIZE) {
      return fail(400, "C007", "페이지 크기 범위를 벗어났습니다.");
    }

    if (!Number.isInteger(offset) || offset < 0) {
      return fail(400, "C006", "커서가 올바르지 않습니다.");
    }

    const terms = query.normalize().toLowerCase().split(/\s+/);

    const matched = placeCatalog.filter((place) => {
      const searchableText = `${place.name.replace(/\s+/g, "")} ${place.address?.replace(/\s+/g, "") ?? ""}`.normalize().toLowerCase();

      const matchesQuery = terms.every((term) => {
        return searchableText.includes(term);
      });

      const matchesRegion = regionCode === null || (place.regionCode?.startsWith(regionCode) ?? false);
      return matchesQuery && matchesRegion;
    });

    const items = matched.slice(offset, offset + limit).map(toPlaceDto);
    const nextOffset = offset + items.length;

    const response: SearchPlacesResponseDto = {
      items,
      nextCursor: nextOffset < matched.length ? String(nextOffset) : null,
      totalCount: matched.length,
    };

    return ok(response);
  }),
];
