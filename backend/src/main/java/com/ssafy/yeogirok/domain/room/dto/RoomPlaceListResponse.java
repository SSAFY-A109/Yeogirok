package com.ssafy.yeogirok.domain.room.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.room.entity.PlaceSourceType;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-028 후보 목록. version 은 여행방 version 이다(빼기·복원 요청에 쓴다).
 * sourceRecord 는 아직 항상 null 이다. 코스에서 가져온 후보(API-030)는 sourceRecordId 만 채운다.
 * TODO 원본 코스가 지금 공개 중이면 제목·작성자를 sourceRecord 로 채운다(API-028 명세의 모양에 맞춰).
 */
public record RoomPlaceListResponse(List<Item> items, Integer version) {

	public record Item(
		Long id,
		PlaceInfo place,
		PlaceSourceType sourceType,
		Long sourceRecordId,
		Object sourceRecord,
		Long addedBy,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
		Instant removedAt
	) {

		public static Item from(RoomPlace place, Map<String, String> categoryNames) {
			return new Item(place.getId(), PlaceInfo.from(place.getPlace(), categoryNames), place.getSourceType(),
				place.getSourceRecordId(), null, place.getAddedBy(), place.getRemovedAt());
		}
	}
}
