package com.ssafy.yeogirok.domain.footprint.entity;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 방명록 본문 정리. 저장·반환하는 값은 이 결과다.
 *
 * <ol>
 *   <li>CRLF·CR 을 LF 로 바꾼다.</li>
 *   <li>빈 문자열·공백만이면 길이와 상관없이 null(11/4 결정).</li>
 *   <li>공백 포함 {@value #MAX_LENGTH}자를 넘으면 C003. 자동으로 자르지 않는다.</li>
 * </ol>
 *
 * <p>길이는 DB 의 {@code char_length} 와 같게 유니코드 문자(code point) 수로 센다. 이모지 하나는 1자다.
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
