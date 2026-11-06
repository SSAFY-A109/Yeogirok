package com.ssafy.yeogirok.domain.room.dto;

import java.util.Map;

import com.ssafy.yeogirok.domain.room.entity.PlaceSourceType;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;

/** 새로 담은 후보(명세의 RoomPlace). API-031 의 roomPlace. */
public record RoomPlaceResponse(Long id, PlaceInfo place, PlaceSourceType sourceType, Long sourceRecordId,
	Long addedBy) {

	public static RoomPlaceResponse from(RoomPlace place, Map<String, String> categoryNames) {
		return new RoomPlaceResponse(place.getId(), PlaceInfo.from(place.getPlace(), categoryNames),
			place.getSourceType(), place.getSourceRecordId(), place.getAddedBy());
	}
}
