package com.ssafy.yeogirok.domain.footprint.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

class FootprintBodyTest {

	@Test
	void 본문이_없으면_null() {
		assertThat(FootprintBody.normalize(null)).isNull();
	}

	@Test
	void 빈_문자열과_공백만은_null() {
		assertThat(FootprintBody.normalize("")).isNull();
		assertThat(FootprintBody.normalize("  \t")).isNull();
		assertThat(FootprintBody.normalize("\r\n\n ")).isNull();
	}

	@Test
	void 공백만이면_200자를_넘어도_null() {
		assertThat(FootprintBody.normalize(" ".repeat(300))).isNull();
	}

	@Test
	void CRLF와_CR을_LF로_바꿔_저장한다() {
		assertThat(FootprintBody.normalize("첫 줄\r\n둘째 줄\r셋째 줄\n넷째 줄"))
			.isEqualTo("첫 줄\n둘째 줄\n셋째 줄\n넷째 줄");
	}

	@Test
	void 앞뒤_공백은_자르지_않는다() {
		assertThat(FootprintBody.normalize("  좋았다 ")).isEqualTo("  좋았다 ");
	}

	@Test
	void 공백_포함_200자는_받고_201자는_C003() {
		assertThat(FootprintBody.normalize("가 ".repeat(100))).hasSize(200);
		expectTooLong("가".repeat(201));
	}

	@Test
	void 길이는_CRLF를_LF로_바꾼_뒤에_센다() {
		String body = "가".repeat(198) + "\r\n" + "나";  // 바꾸기 전 201자, 바꾼 뒤 200자

		assertThat(FootprintBody.normalize(body)).isEqualTo("가".repeat(198) + "\n" + "나");
	}

	@Test
	void 이모지는_한_글자로_센다() {
		String emoji = "😀";  // 😀, UTF-16 으로는 2칸

		assertThat(FootprintBody.normalize(emoji.repeat(200))).isNotNull();
		expectTooLong(emoji.repeat(201));
	}

	private static void expectTooLong(String body) {
		assertThatThrownBy(() -> FootprintBody.normalize(body))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.NOT_ALLOWED_VALUE);
	}
}
