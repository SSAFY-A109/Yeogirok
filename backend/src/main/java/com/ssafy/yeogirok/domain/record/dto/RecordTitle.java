package com.ssafy.yeogirok.domain.record.dto;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.ControlCharacters;

/**
 * 기록·코스 제목 정리(API-057·072·073·075). 제어 문자(줄바꿈·탭 제외)가 있거나, 앞뒤 공백을 지운 뒤 비었거나 200자 초과면
 * C003(여행방 제목과 같은 길이 규칙).
 */
public final class RecordTitle {

	private RecordTitle() {
	}

	public static String normalize(String value) {
		String trimmed = ControlCharacters.reject(value, "title").strip();
		// DB VARCHAR 길이와 같게 코드 포인트로 센다.
		int length = trimmed.codePointCount(0, trimmed.length());
		if (length == 0 || length > TravelRecord.TITLE_MAX_LENGTH) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"title: 1~%d자여야 합니다.".formatted(TravelRecord.TITLE_MAX_LENGTH));
		}
		return trimmed;
	}
}
