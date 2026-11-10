package com.ssafy.yeogirok.domain.tip.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

class ArTipCategoryTest {

	@ParameterizedTest
	@EnumSource(ArTipCategory.class)
	void 다섯_종류는_받는다(ArTipCategory category) {
		assertThat(ArTipCategory.fromCode(category.name())).isEqualTo(category);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "FOOD", "photo", "NATURE", " PHOTO"})
	void 목록_밖이면_C003(String code) {
		assertThatThrownBy(() -> ArTipCategory.fromCode(code))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.NOT_ALLOWED_VALUE);
	}
}
