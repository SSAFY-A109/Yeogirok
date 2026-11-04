package com.ssafy.yeogirok.domain.category;

import java.util.Arrays;
import java.util.Optional;

/**
 * 공통 카테고리 8개. 발자취·장소·공개 코스가 함께 쓴다. 숙박은 없다.
 *
 * <p>DB와 API에는 상수 이름({@link #name()})을 코드로 쓴다. <b>이름을 바꾸면 저장된 데이터와 앱 동작이 바뀐다.</b>
 */
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
