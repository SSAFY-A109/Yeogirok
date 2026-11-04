package com.ssafy.yeogirok.domain.footprint.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

class FootprintCategoriesTest {

	@Test
	void 공통_카테고리_1개부터_3개까지_고른_순서대로_받는다() {
		assertThat(FootprintCategories.validate(List.of("CAFE"))).containsExactly("CAFE");
		assertThat(FootprintCategories.validate(List.of("FOOD", "NATURE", "EVENT")))
			.containsExactly("FOOD", "NATURE", "EVENT");
	}

	@Test
	void 공통_카테고리_8개_모두_받는다() {
		for (String code : List.of("NATURE", "CULTURE", "SIGHT", "FOOD", "CAFE", "ACTIVITY", "SHOPPING", "EVENT")) {
			assertThat(FootprintCategories.validate(List.of(code))).containsExactly(code);
		}
	}

	@Test
	void 빈_배열은_C003() {
		expectNotAllowed(List.of());
	}

	@Test
	void 네_개_이상은_C003() {
		expectNotAllowed(List.of("FOOD", "CAFE", "NATURE", "EVENT"));
	}

	@Test
	void 중복은_C003() {
		expectNotAllowed(List.of("FOOD", "FOOD"));
	}

	@Test
	void 없는_코드는_C003() {
		expectNotAllowed(List.of("LODGING"));
		expectNotAllowed(List.of("cafe"));
		expectNotAllowed(Arrays.asList("FOOD", null));
	}

	@Test
	void 목록_자체가_없으면_C001() {
		assertThatThrownBy(() -> FootprintCategories.validate(null))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.REQUIRED_VALUE_MISSING);
	}

	private static void expectNotAllowed(List<String> codes) {
		assertThatThrownBy(() -> FootprintCategories.validate(codes))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.NOT_ALLOWED_VALUE);
	}
}
