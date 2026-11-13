package com.ssafy.yeogirok.domain.room.dto;

import java.util.List;

/** API-030 응답. added 는 요청 순서대로 새로 만든 후보(sourceType=COURSE), version 은 가져온 뒤의 여행방 version 이다. */
public record RoomPlaceImportResponse(List<RoomPlaceResponse> added, int version) {

	public RoomPlaceImportResponse {
		added = List.copyOf(added);
	}
}
