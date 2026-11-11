package com.ssafy.yeogirok.domain.course.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 188 API-014 검색 조건 값. 지원하지 않는 필터 CO002, 정렬 CO003. */
class CourseSearchConditionTest {

	@ParameterizedTest(name = "{0}={1} → {2}")
	@CsvSource({
		"sort, LATEST, COURSE_SORT_NOT_SUPPORTED",
		"sort, popular, COURSE_SORT_NOT_SUPPORTED",
		"regionCode, 5, COURSE_FILTER_NOT_SUPPORTED",
		"regionCode, 511, COURSE_FILTER_NOT_SUPPORTED",
		"regionCode, 5115010100, COURSE_FILTER_NOT_SUPPORTED",
		"regionCode, 5A, COURSE_FILTER_NOT_SUPPORTED",
		"category, LODGING, COURSE_FILTER_NOT_SUPPORTED",
		"category, nature, COURSE_FILTER_NOT_SUPPORTED",
		"duration, THREE_DAYS, COURSE_FILTER_NOT_SUPPORTED",
		"transportMode, WALK, COURSE_FILTER_NOT_SUPPORTED"
	})
	void 지원하지_않는_값(String field, String value, ExceptionType expected) {
		assertThatThrownBy(() -> of(field, value)).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(expected);
	}

	@Test
	void 검색어는_100자까지() {
		assertThat(of("q", "가".repeat(100)).pattern()).isEqualTo("%" + "가".repeat(100) + "%");
		assertThatThrownBy(() -> of("q", "가".repeat(101))).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.COURSE_FILTER_NOT_SUPPORTED);
	}

	@Test
	void 생략하면_거르지_않고_공백뿐인_검색어도_무시한다() {
		assertThat(CourseSearchCondition.of("  ", null, null, null, null, null))
			.isEqualTo(new CourseSearchCondition(null, null, null, CourseSearchCondition.Duration.ALL, null));
	}

	@Test
	void 받은_값을_조회용으로_바꾼다() {
		assertThat(CourseSearchCondition.of(" Coffee_100% ", "51150", "CAFE", "POPULAR", "DAY_TRIP", "TRANSIT"))
			.isEqualTo(new CourseSearchCondition("%coffee!_100!%%", "51150%", "CAFE",
				CourseSearchCondition.Duration.DAY_TRIP, TransportMode.TRANSIT));
	}

	private static CourseSearchCondition of(String field, String value) {
		return CourseSearchCondition.of(field.equals("q") ? value : null, field.equals("regionCode") ? value : null,
			field.equals("category") ? value : null, field.equals("sort") ? value : null,
			field.equals("duration") ? value : null, field.equals("transportMode") ? value : null);
	}
}
