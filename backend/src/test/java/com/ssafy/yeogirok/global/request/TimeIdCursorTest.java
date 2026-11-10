package com.ssafy.yeogirok.global.request;

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

class TimeIdCursorTest {

	@Test
	void 만든_커서를_그대로_다시_읽고_값이_없으면_null() {
		TimeIdCursor cursor = new TimeIdCursor(Instant.parse("2026-11-04T03:00:00.123456Z"), 15L);

		assertThat(TimeIdCursor.decodeOrNull(cursor.encode())).isEqualTo(cursor);
		assertThat(TimeIdCursor.decodeOrNull(null)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "%%%", "bm90LWEtY3Vyc29y"})  // 빈 값, base64 아님, 구분자 없음("not-a-cursor")
	void 해석할_수_없으면_C006(String cursor) {
		expectInvalid(cursor);
	}

	@ParameterizedTest
	@ValueSource(strings = {"어제|15", "2026-11-04T03:00:00Z|abc", "2026-11-04T03:00:00Z|0", "2026-11-04T03:00:00Z|-1"})
	void 시각이나_id가_틀리면_C006(String raw) {
		expectInvalid(encode(raw));
	}

	private static String encode(String raw) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private static void expectInvalid(String cursor) {
		assertThatThrownBy(() -> TimeIdCursor.decodeOrNull(cursor))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.INVALID_CURSOR);
	}
}
