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

	/** @return 장소 행이 없으면 null(FK 가 없어 지워진 행을 가리킬 수 있다. 071 은 이 방문을 RE016 으로 막는다) */
	public static RecordPlaceView from(Place place) {
		if (place == null) {
			return null;
		}
		return new RecordPlaceView(place.getProvider(), place.getProviderPlaceId(), place.getName(),
			place.getLatitude(), place.getLongitude(), place.getAddress(), place.getCategoryCode(),
			place.getImageUrl(), place.getImageAttribution());
	}
}
