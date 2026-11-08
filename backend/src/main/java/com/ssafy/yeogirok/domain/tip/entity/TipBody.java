package com.ssafy.yeogirok.domain.tip.entity;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * AR 팁 본문 정리. CRLF·CR→LF 후 비었거나 공백만이거나 {@value #MAX_LENGTH}자 초과면 C003.
 * 길이는 DB {@code char_length} 와 같게 code point 수로 센다. 발자취와 달리 공백만이면 거부라 함수를 나눈다.
 */
public final class TipBody {

	public static final int MAX_LENGTH = 120;

	private TipBody() {
	}

	/** @param raw 생략·null 은 요청 DTO 가 먼저 C001 로 거른다 */
	public static String normalize(String raw) {
		String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
		if (normalized.isBlank()) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "body: 내용을 입력해 주세요.");
		}
		if (normalized.codePointCount(0, normalized.length()) > MAX_LENGTH) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"body: 최대 %d자까지 쓸 수 있습니다.".formatted(MAX_LENGTH));
		}
		return normalized;
	}
}
