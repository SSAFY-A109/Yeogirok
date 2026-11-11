package com.ssafy.yeogirok.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ssafy.yeogirok.domain.place.entity.Place;

/** 검색 카드 지역명: 주소 첫 단어가 시·도 이름일 때만 주소, 아니면 법정동 코드의 시·도 이름. */
class CourseRegionsTest {

	@ParameterizedTest(name = "주소 {0}, 법정동 {1} → {2}")
	@CsvSource(nullValues = "-", value = {
		"강원특별자치도 강릉시 창해로 365, 5115010100, 강릉시",
		"강원 강릉시 교동, -, 강릉시",
		"서울특별시 종로구 사직로 161, 1111010100, 종로구",
		"세종특별자치시 한누리대로 2130, 3611010100, 세종특별자치시",
		"우리집 근처, 5115010100, 강원특별자치도",   // 직접 입력 장소의 자유 주소
		"우리집 근처, -, ''",
		"-, 5011010100, 제주특별자치도",
		"-, -, ''"
	})
	void 지역명(String address, String regionCode, String expected) {
		Place place = Place.manual("장소", new BigDecimal("37.7"), new BigDecimal("128.9"), address, regionCode, null,
			Instant.parse("2026-11-11T00:00:00Z"));

		assertThat(CourseRegions.nameOf(place)).isEqualTo(expected);
	}
}
