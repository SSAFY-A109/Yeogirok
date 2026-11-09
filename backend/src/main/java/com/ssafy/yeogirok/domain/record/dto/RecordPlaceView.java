package com.ssafy.yeogirok.domain.record.dto;

import java.math.BigDecimal;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;

/** 실제 방문의 장소(노션 059·067 Place). places 를 조인해 만든다. categoryName 은 없다. */
public record RecordPlaceView(
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

	public static RecordPlaceView from(Place place) {
		return new RecordPlaceView(place.getProvider(), place.getProviderPlaceId(), place.getName(),
			place.getLatitude(), place.getLongitude(), place.getAddress(), place.getCategoryCode(),
			place.getImageUrl(), place.getImageAttribution());
	}
}
