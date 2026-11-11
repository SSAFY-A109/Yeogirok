package com.ssafy.yeogirok.domain.course.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;

/** API-014 응답(노션 그대로). totalCount 는 같은 조건의 전체 개수(커서와 무관). */
public record CourseSearchResponse(List<Item> items, int totalCount, String nextCursor) {

	/**
	 * @param regionName   첫 방문 장소의 시·군·구(없으면 빈 문자열)
	 * @param thumbnailUrl 첫 공개 사진, 없으면 첫 방문 장소 이미지, 둘 다 없으면 null
	 * @param placeCount   실제 방문 수
	 * @param routePreview 방문 순서대로 장소 이름 최대 5개
	 * @param importCount  이 코스에서 장소를 가져간 미삭제 여행방 수
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
		List<String> routePreview,
		long importCount
	) {
	}
}
