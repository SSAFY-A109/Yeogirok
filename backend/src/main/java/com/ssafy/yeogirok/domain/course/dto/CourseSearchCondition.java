package com.ssafy.yeogirok.domain.course.dto;

import java.util.Arrays;
import java.util.Locale;

import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.ControlCharacters;
import com.ssafy.yeogirok.global.request.RegionCode;

/**
 * API-014 검색 조건(명세 {@code specs/course-search-api.md} 2·5절 2~3). 모두 선택이고 생략하면 거르지 않는다. 지원하지 않는
 * 필터 값 CO002, 정렬 CO003, q 의 제어 문자(줄바꿈·탭 제외) C003.
 *
 * @param pattern      q 를 소문자 LIKE 패턴으로(없으면 null)
 * @param regionPrefix regionCode LIKE 패턴(없으면 null)
 */
public record CourseSearchCondition(String pattern, String regionPrefix, String category, Duration duration,
	TransportMode transportMode) {

	public static final int QUERY_MAX_LENGTH = 100;
	private static final String POPULAR = "POPULAR";

	/** 여행 기간(dayCount 기준). */
	public enum Duration {
		ALL, DAY_TRIP, ONE_NIGHT_TWO_DAYS, TWO_NIGHTS_OR_MORE;

		public boolean matches(int dayCount) {
			return switch (this) {
				case ALL -> true;
				case DAY_TRIP -> dayCount == 1;
				case ONE_NIGHT_TWO_DAYS -> dayCount == 2;
				case TWO_NIGHTS_OR_MORE -> dayCount >= 3;
			};
		}
	}

	public static CourseSearchCondition of(String q, String regionCode, String category, String sort, String duration,
		String transportMode) {
		if (sort != null && !POPULAR.equals(sort)) {
			throw new BusinessException(ExceptionType.COURSE_SORT_NOT_SUPPORTED, "sort: POPULAR 만 지원합니다.");
		}
		// U+0000 은 PostgreSQL 이 받지 못해 500 이 된다(H2 는 통과). 제목·설명과 같은 검사(PR #94 리뷰).
		String query = q == null ? "" : ControlCharacters.reject(q, "q").strip();
		if (query.codePointCount(0, query.length()) > QUERY_MAX_LENGTH) {
			throw unsupported("q: 최대 %d자입니다.".formatted(QUERY_MAX_LENGTH));
		}
		String regionPrefix = regionPrefix(regionCode, ExceptionType.COURSE_FILTER_NOT_SUPPORTED);
		if (category != null && TravelCategory.fromCode(category).isEmpty()) {
			throw unsupported("category: 없는 카테고리입니다.");
		}
		return new CourseSearchCondition(query.isEmpty() ? null : "%" + escape(query.toLowerCase(Locale.ROOT)) + "%",
			regionPrefix, category,
			duration == null ? Duration.ALL : parse(Duration.class, duration, "duration"),
			transportMode == null ? null : parse(TransportMode.class, transportMode, "transportMode"));
	}

	/** LIKE 이스케이프 문자는 '!'(저장소 쿼리와 같다). */
	private static String escape(String value) {
		return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}

	/**
	 * regionCode 를 법정동 코드 앞자리 LIKE 패턴으로(014·015 공통). 오류 코드는 API 마다 달라(014 CO002, 015 C003) 받는다.
	 *
	 * @return regionCode 가 null 이면 null
	 */
	public static String regionPrefix(String regionCode, ExceptionType onInvalid) {
		RegionCode.check(regionCode, onInvalid);
		return regionCode == null ? null : regionCode + "%";
	}

	private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
		return Arrays.stream(type.getEnumConstants()).filter(constant -> constant.name().equals(value)).findFirst()
			.orElseThrow(() -> unsupported(field + ": 지원하지 않는 값입니다."));
	}

	private static BusinessException unsupported(String message) {
		return new BusinessException(ExceptionType.COURSE_FILTER_NOT_SUPPORTED, message);
	}
}
