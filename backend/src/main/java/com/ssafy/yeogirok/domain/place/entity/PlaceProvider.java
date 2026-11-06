package com.ssafy.yeogirok.domain.place.entity;

/** 장소 공급자. TOUR_API 는 providerPlaceId 가 있고, MANUAL(지도 핀 직접 추가)은 없다. */
public enum PlaceProvider {
	TOUR_API,
	MANUAL
}
