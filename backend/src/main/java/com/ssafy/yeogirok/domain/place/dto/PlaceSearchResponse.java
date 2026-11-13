package com.ssafy.yeogirok.domain.place.dto;

import java.math.BigDecimal;
import java.util.List;

import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.tour.TourPlace;

/**
 * API-026 응답.
 *
 * @param nextCursor 다음 쪽이 없으면 null
 * @param totalCount TourAPI 가 준 같은 q·regionCode 조건의 전체 결과 수
 */
public record PlaceSearchResponse(List<Item> items, String nextCursor, int totalCount) {

	public PlaceSearchResponse {
		items = List.copyOf(items);
	}

	/** 검색 결과 장소. 지금은 TourAPI 장소만 나온다(providerPlaceId 는 contentId). categoryCode 는 공통 카테고리 코드다. */
	public record Item(
		PlaceProvider provider,
		String providerPlaceId,
		String name,
		BigDecimal latitude,
		BigDecimal longitude,
		String address,
		String categoryCode,
		String imageUrl,
		String imageAttribution
	) {

		public static Item from(TourPlace place) {
			return new Item(PlaceProvider.TOUR_API, place.contentId(), place.title(), place.latitude(),
				place.longitude(), place.address(), place.categoryCode(), place.imageUrl(), place.imageAttribution());
		}
	}
}
