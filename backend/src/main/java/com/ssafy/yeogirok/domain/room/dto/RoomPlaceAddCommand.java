package com.ssafy.yeogirok.domain.room.dto;

import java.util.List;

/**
 * API-029 검사를 끝낸 요청. 지금은 추천 담기(sourceType=AI)만 받는다. 멱등키 요청 해시에도 쓴다.
 *
 * @param items 담는 순서. 1개 이상
 */
public record RoomPlaceAddCommand(List<Item> items) {

	public RoomPlaceAddCommand {
		items = List.copyOf(items);
	}

	/** 추천 결과에서 고른 투어API 장소 하나. */
	public record Item(String providerPlaceId, Long recommendationRequestId, int rank) {
	}
}
