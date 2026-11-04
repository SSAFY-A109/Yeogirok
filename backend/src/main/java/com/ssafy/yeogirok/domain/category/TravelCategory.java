package com.ssafy.yeogirok.domain.category;

import java.util.Arrays;
import java.util.Optional;

/** 공통 카테고리 8개(숙박 없음). 상수 이름이 DB·API 코드라 바꾸면 저장된 데이터와 앱 동작이 바뀐다. */
public enum TravelCategory {
	NATURE,
	CULTURE,
	SIGHT,
	FOOD,
	CAFE,
	ACTIVITY,
	SHOPPING,
	EVENT;

	/** 코드가 정확히 일치할 때만 찾는다(대소문자 구분). */
	public static Optional<TravelCategory> fromCode(String code) {
		return Arrays.stream(values())
			.filter(category -> category.name().equals(code))
			.findFirst();
	}
}
