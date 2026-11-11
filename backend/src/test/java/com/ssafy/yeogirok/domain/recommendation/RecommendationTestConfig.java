package com.ssafy.yeogirok.domain.recommendation;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.recommendation.service.PlaceRecommendationService;
import com.ssafy.yeogirok.domain.recommendation.service.RecommendationPreferenceService;
import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.support.FakeTourApiClient;

import tools.jackson.databind.json.JsonMapper;

/** 추천 서비스 @DataJpaTest 용 빈. 여행방 빈({@link RoomTestConfig})을 함께 쓴다. */
@TestConfiguration
@Import({RoomTestConfig.class, RecommendationPreferenceService.class, PlaceRecommendationService.class})
public class RecommendationTestConfig {

	/** 기본 10개·상한 50개, 조회당 100개, 호출 상한 12번·마감 1초, 인기 30일, 기준거리 CAR 20km·TRANSIT 7km. */
	public static final RecommendationProperties PROPERTIES = new RecommendationProperties(10, 50, 100, 12,
		Duration.ofSeconds(1), Duration.ofDays(30), 20_000, 7_000);

	@Bean
	RecommendationProperties recommendationProperties() {
		return PROPERTIES;
	}

	@Bean
	FakeTourApiClient tourApiClient() {
		return new FakeTourApiClient();
	}

	@Bean
	JsonMapper jsonMapper() {
		return JsonMapper.builder().build();
	}
}
