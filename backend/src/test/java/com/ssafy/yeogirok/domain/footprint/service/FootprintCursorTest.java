package com.ssafy.yeogirok.domain.footprint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

class FootprintCursorTest {

	@Test
	void 만든_커서를_그대로_다시_읽는다() {
		FootprintCursor cursor = new FootprintCursor(Instant.parse("2026-11-04T03:00:00.123456Z"), 15L);

		assertThat(FootprintCursor.decodeOrNull(cursor.encode())).isEqualTo(cursor);
	}

	@Test
	void 값이_없으면_null() {
		assertThat(FootprintCursor.decodeOrNull(null)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "%%%", "bm90LWEtY3Vyc29y"})  // 빈 값, base64 아님, 구분자 없음("not-a-cursor")
	void 해석할_수_없으면_C006(String cursor) {
		expectInvalid(cursor);
	}

	@Test
	void 시각이나_id가_틀리면_C006() {
		expectInvalid(encode("어제|15"));
		expectInvalid(encode("2026-11-04T03:00:00Z|abc"));
		expectInvalid(encode("2026-11-04T03:00:00Z|0"));
		expectInvalid(encode("2026-11-04T03:00:00Z|-1"));
	}

	private static String encode(String raw) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private static void expectInvalid(String cursor) {
		assertThatThrownBy(() -> FootprintCursor.decodeOrNull(cursor))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.INVALID_CURSOR);
	}
}
