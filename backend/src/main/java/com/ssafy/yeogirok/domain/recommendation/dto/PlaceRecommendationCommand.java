package com.ssafy.yeogirok.domain.recommendation.dto;

import java.util.List;

/**
 * API-034 검사를 끝낸 요청. 멱등키 요청 해시에도 쓴다.
 *
 * @param limit          요청한 개수. 생략이면 null(기본값은 서비스가 정한다)
 * @param excludedPlaces 결과에서 뺄 장소. 생략이면 빈 목록
 */
public record PlaceRecommendationCommand(Integer limit, List<PlaceRef> excludedPlaces) {

	public static final PlaceRecommendationCommand EMPTY = new PlaceRecommendationCommand(null, List.of());

	public PlaceRecommendationCommand {
		excludedPlaces = List.copyOf(excludedPlaces);
	}
}
