package com.ssafy.yeogirok.global.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 제목·설명 제어 문자: 줄바꿈·탭은 받고 나머지는 C003. */
class ControlCharactersTest {

	@ParameterizedTest
	@ValueSource(strings = {"강릉 1박", "첫째 줄\n둘째 줄", "a\r\nb", "탭\t구분", ""})
	void 줄바꿈과_탭은_받는다(String value) {
		assertThat(ControlCharacters.reject(value, "description")).isEqualTo(value);
	}

	@ParameterizedTest
	@ValueSource(strings = {"a\u0000b", "a\u001Fb", "a\u007Fb", "\u0008"})
	void 그_밖의_제어_문자는_C003(String value) {
		assertThatThrownBy(() -> ControlCharacters.reject(value, "description")).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.NOT_ALLOWED_VALUE);
	}
}
