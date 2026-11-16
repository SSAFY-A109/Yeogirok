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
 * sourceRecord 는 코스에서 가져온 후보(API-030)의 원본 코스가 지금 공개 중일 때만 채운다({@link SourceRecordResponse}).
 */
public record RoomPlaceListResponse(
	List<Item> items,
	Integer version
) {

	public record Item(
		Long id,
		PlaceInfo place,
		PlaceSourceType sourceType,
		Long sourceRecordId,
		SourceRecordResponse sourceRecord,
		Long addedBy,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
		Instant removedAt
	) {

		/** @param sourceRecords 원본 기록 ID → 지금 볼 수 있는 원본 코스({@code RoomPlaceSources}) */
		public static Item from(RoomPlace place, Map<String, String> categoryNames,
			Map<Long, SourceRecordResponse> sourceRecords) {
			return new Item(place.getId(), PlaceInfo.from(place.getPlace(), categoryNames), place.getSourceType(),
				place.getSourceRecordId(), SourceRecordResponse.of(place, sourceRecords), place.getAddedBy(),
				place.getRemovedAt());
		}
	}
}
