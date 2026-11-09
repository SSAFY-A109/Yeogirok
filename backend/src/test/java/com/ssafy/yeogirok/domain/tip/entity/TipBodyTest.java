package com.ssafy.yeogirok.domain.tip.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

class TipBodyTest {

	@Test
	void 공백_포함_120자는_받고_CRLF는_LF로_바꾼다() {
		assertThat(TipBody.normalize("가 ".repeat(60))).hasSize(120);
		assertThat(TipBody.normalize("첫 줄\r\n둘째\r셋째")).isEqualTo("첫 줄\n둘째\n셋째");
		// 바꾸기 전 121자, 바꾼 뒤 120자
		assertThat(TipBody.normalize("가".repeat(118) + "\r\n나")).hasSize(120);
	}

	@Test
	void 이모지는_한_글자로_센다() {
		assertThat(TipBody.normalize("😀".repeat(120))).isNotNull();
		expectC003("😀".repeat(121));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", "\r\n\t"})
	void 비었거나_공백만이면_C003(String body) {
		expectC003(body);
	}

	@Test
	void 공백_포함_121자는_C003() {
		expectC003("가".repeat(120) + " ");
	}

	private static void expectC003(String body) {
		assertThatThrownBy(() -> TipBody.normalize(body))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.NOT_ALLOWED_VALUE);
	}
}
