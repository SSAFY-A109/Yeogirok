package com.ssafy.yeogirok.domain.room.dto;

import java.util.List;

/** API-029 응답. added 는 요청 순서대로 새로 만든 후보, version 은 담은 뒤의 여행방 version 이다. */
public record RoomPlaceAddResponse(List<RoomPlaceResponse> added, int addedCount, int version) {

	public static RoomPlaceAddResponse of(List<RoomPlaceResponse> added, int version) {
		return new RoomPlaceAddResponse(List.copyOf(added), added.size(), version);
	}
}
