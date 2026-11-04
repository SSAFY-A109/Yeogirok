package com.ssafy.yeogirok.domain.footprint.entity;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 방명록 본문 정리. CRLF·CR→LF, 공백만이면 null, {@value #MAX_LENGTH}자 초과면 C003(자르지 않음).
 * 길이는 DB {@code char_length} 와 같게 code point 수로 센다.
 */
public final class FootprintBody {

	public static final int MAX_LENGTH = 200;

	private FootprintBody() {
	}

	/** @return 정리한 본문. 본문이 없으면 null */
	public static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
		if (normalized.isBlank()) {
			return null;
		}
		if (normalized.codePointCount(0, normalized.length()) > MAX_LENGTH) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"body: 최대 %d자까지 쓸 수 있습니다.".formatted(MAX_LENGTH));
		}
		return normalized;
	}
}
