package com.ssafy.yeogirok.domain.room.repository;

/** 외부 장소 하나를 담은 여행방 수. */
public record PlacePopularity(String providerPlaceId, Long roomCount) {
}
