package com.ssafy.yeogirok.domain.course.service;

import java.time.Instant;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * API-015 커서(명세 {@code specs/main-api.md} 5절 6). 최초 조회 시각(집계 끝, 초)과 마지막 정렬 키(30일 가져간 방 수, 코스 id)를
 * 담아 다음 페이지가 같은 30일 구간을 쓴다. 해석 불가 C006.
 */
record MainCourseCursor(Instant windowEnd, long importCount, long id) {

	String encode() {
		return CourseSearchCursor.encodeParts(windowEnd.getEpochSecond(), importCount, id);
	}

	boolean precedes(long otherImportCount, long otherId) {
		return new CourseSearchCursor(importCount, id).precedes(otherImportCount, otherId);
	}

	/**
	 * 최초 조회 시각이 지금보다 뒤면 C006(이 서버가 만든 커서가 아니다). 이 검사로 시각 변환·DB 시각 범위를 넘는 값도 막는다.
	 *
	 * @return 값이 없으면 null
	 */
	static MainCourseCursor decodeOrNull(String cursor, Instant now) {
		long[] parts = CourseSearchCursor.decodeParts(cursor, 3);
		if (parts == null) {
			return null;
		}
		if (parts[0] > now.getEpochSecond()) {
			throw new BusinessException(ExceptionType.INVALID_CURSOR);
		}
		return new MainCourseCursor(Instant.ofEpochSecond(parts[0]), parts[1], parts[2]);
	}
}
