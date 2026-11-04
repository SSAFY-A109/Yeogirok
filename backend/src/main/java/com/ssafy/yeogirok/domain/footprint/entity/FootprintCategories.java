package com.ssafy.yeogirok.domain.footprint.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 발자취 카테고리 검사. 공통 카테고리 1~3개, 중복 없음. 어긋나면 C003. */
public final class FootprintCategories {

	public static final int MIN_COUNT = 1;
	public static final int MAX_COUNT = 3;

	private FootprintCategories() {
	}

	/** @return 고른 순서를 유지한 변경 불가 목록. null 이면 C001 */
	public static List<String> validate(List<String> codes) {
		if (codes == null) {
			throw new BusinessException(ExceptionType.REQUIRED_VALUE_MISSING, "categoryCodes: 값이 필요합니다.");
		}
		if (codes.size() < MIN_COUNT || codes.size() > MAX_COUNT) {
			throw notAllowed("%d~%d개를 골라야 합니다.".formatted(MIN_COUNT, MAX_COUNT));
		}

		Set<String> seen = new HashSet<>();
		for (String code : codes) {
			if (TravelCategory.fromCode(code).isEmpty()) {
				throw notAllowed("없는 카테고리입니다: " + code);
			}
			if (!seen.add(code)) {
				throw notAllowed("중복된 카테고리입니다: " + code);
			}
		}
		return List.copyOf(codes);
	}

	private static BusinessException notAllowed(String reason) {
		return new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "categoryCodes: " + reason);
	}
}
