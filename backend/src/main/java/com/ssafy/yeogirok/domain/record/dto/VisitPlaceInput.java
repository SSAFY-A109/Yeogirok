package com.ssafy.yeogirok.domain.record.dto;

import java.math.BigDecimal;

/** 실제 방문의 장소 입력(API-066·067). 투어API 장소 참조이거나 직접 입력이다. */
public sealed interface VisitPlaceInput {

	/** placeRef(provider=TOUR_API). */
	record TourPlace(String providerPlaceId) implements VisitPlaceInput {
	}

	/** customPlace. name 은 앞뒤 공백을 지우고 길이를 확인한 값, 좌표는 범위를 확인한 값이다. */
	record CustomPlace(String name, BigDecimal latitude, BigDecimal longitude, String address)
		implements VisitPlaceInput {
	}
}
