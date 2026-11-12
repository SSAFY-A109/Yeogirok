package com.ssafy.yeogirok.domain.course.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;

/** API-015 응답(노션 그대로). 이야기·방명록·동선·닉네임은 없다. */
public record MainCourseListResponse(List<Item> items, String nextCursor, ImportCountBasis basis) {

	/**
	 * @param thumbnailUrl  첫 공개 방문 사진(노출 조건상 항상 있다)
	 * @param placeCount    방문 수
	 * @param previewPlaces 공개 사진이 있는 방문 중 날짜별 첫 방문, 일차 순 최대 3개
	 */
	public record Item(
		Long id,
		String title,
		List<PublicAuthor> authors,
		String regionName,
		String thumbnailUrl,
		int placeCount,
		int dayCount,
		int nightCount,
		TransportMode transportMode,
		long importCount30d,
		List<PreviewPlace> previewPlaces
	) {
	}

	/** @param imageUrl 그 방문의 첫 공개 사진 */
	public record PreviewPlace(Long visitId, int day, String name, String categoryCode, String categoryName,
		String imageUrl) {
	}
}
