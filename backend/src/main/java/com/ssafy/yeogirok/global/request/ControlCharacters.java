package com.ssafy.yeogirok.global.request;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 사람이 쓰는 글(제목·설명)의 제어 문자 검사. 줄바꿈·탭은 받고 나머지(U+0000 등)는 C003. PostgreSQL 은 U+0000 을 저장하지
 * 못해 500 이 되고(H2 는 통과), 코스 selectionHash 의 구분 문자(U+001F)도 여기서 막힌다(PR #89 리뷰).
 */
public final class ControlCharacters {

	private ControlCharacters() {
	}

	public static String reject(String value, String field) {
		boolean found = value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t');
		if (found) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, field + ": 제어 문자는 쓸 수 없습니다.");
		}
		return value;
	}
}
