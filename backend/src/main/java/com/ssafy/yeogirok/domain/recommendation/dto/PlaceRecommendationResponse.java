package com.ssafy.yeogirok.domain.recommendation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationMode;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-034 응답. 같은 값을 recommendation_logs.result_payload 에 저장해 두고 멱등키 재전송에 그대로 돌려준다.
 *
 * @param recommendationRequestId 추천 요청 기록(recommendation_logs REQUEST) ID. 담기(API-029) 때 함께 보낸다
 */
public record PlaceRecommendationResponse(
	Long recommendationRequestId,
	RecommendationMode mode,
	List<Item> items,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant generatedAt
) {

	/** @param reasonEvidence reason 을 만든 근거(점수에 쓴 항목만) */
	public record Item(int rank, RecommendedPlace place, String reason, List<String> reasonEvidence) {
	}

	/**
	 * 검색·추천 응답의 장소(명세의 Place). 카테고리 이름은 주지 않고 코드만 준다(앱이 API-079 로 찾는다).
	 * 사진이 있으면 출처는 한국관광공사다.
	 */
	public record RecommendedPlace(
		String provider,
		String providerPlaceId,
		String name,
		BigDecimal latitude,
		BigDecimal longitude,
		String address,
		String categoryCode,
		String imageUrl,
		String imageAttribution
	) {
	}
}
