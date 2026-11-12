package com.ssafy.yeogirok.domain.recommendation;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 장소 추천(API-034) 설정. 값의 뜻은 application.yaml 주석과 노션 API-034 "추천 방식"을 따른다.
 * 카테고리 ↔ TourAPI 분류는 설정이 아니라 확정 매핑({@link com.ssafy.yeogirok.domain.place.tour.TourCategoryMapper})을 쓴다.
 *
 * @param rowsPerQuery    TourAPI 조회 한 번에 받을 항목 수
 * @param maxTourApiCalls 추천 한 번의 TourAPI 호출 상한. 대분류 수(8)보다 작으면 모든 카테고리를 고를 때 지킬 수 없다
 * @param fetchTimeout    TourAPI 동시 호출 전체를 기다리는 시간. 멱등키 처리 중 표시 수명보다 충분히 짧아야 한다
 */
@Validated
@ConfigurationProperties("yeogirok.recommendation")
public record RecommendationProperties(
	@Positive int defaultLimit,
	@Positive int maxLimit,
	@Positive int rowsPerQuery,
	@Min(8) int maxTourApiCalls,
	@NotNull Duration fetchTimeout,
	@NotNull Duration popularWindow,
	@Positive int carRadiusMeters,
	@Positive int transitRadiusMeters
) {
}
