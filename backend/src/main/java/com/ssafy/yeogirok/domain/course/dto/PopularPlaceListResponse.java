package com.ssafy.yeogirok.domain.course.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.room.dto.PlaceInfo;

/** API-093 응답(노션 그대로). place 는 여행방 후보와 같은 공통 Place(명세 main-api.md 5절 10). */
public record PopularPlaceListResponse(List<Item> items, ImportCountBasis basis) {

	/** @param rank 1부터 */
	public record Item(int rank, PlaceInfo place, long importCount30d) {
	}
}
