package com.ssafy.yeogirok.domain.recommendation.dto;

/** 외부 장소 참조. 추천은 TourAPI 장소만 다룬다. */
public record PlaceRef(String provider, String providerPlaceId) {
}
