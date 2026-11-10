package com.ssafy.yeogirok.domain.course.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;

/** 공개 코스의 장소(노션 016 Place). 기록 응답(059)과 달리 categoryName 이 있다. */
public record PublicPlace(
	PlaceProvider provider,
	String providerPlaceId,
	String name,
	BigDecimal latitude,
	BigDecimal longitude,
	String address,
	String categoryCode,
	String categoryName,
	String imageUrl,
	String imageAttribution
) {

	/** @return 장소 행이 없으면 null(FK 가 없다. 완성한 기록은 071 이 이 경우를 막는다) */
	public static PublicPlace from(Place place, Map<String, String> categoryNames) {
		if (place == null) {
			return null;
		}
		String code = place.getCategoryCode();
		// 빈 Map.of() 는 null 키 조회에서 예외를 던진다.
		return new PublicPlace(place.getProvider(), place.getProviderPlaceId(), place.getName(), place.getLatitude(),
			place.getLongitude(), place.getAddress(), code, code == null ? null : categoryNames.get(code),
			place.getImageUrl(), place.getImageAttribution());
	}
}
