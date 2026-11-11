package com.ssafy.yeogirok.domain.course.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** API-014 커서. 정렬 키(가져간 방 수 내림차순, 기록 id 내림차순)의 마지막 값 base64url("{importCount}:{id}"), 해석 불가 C006. */
record CourseSearchCursor(long importCount, long id) {

	String encode() {
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString((importCount + ":" + id).getBytes(StandardCharsets.UTF_8));
	}

	/** 정렬상 이 커서 뒤에 오는지. */
	boolean precedes(long otherImportCount, long otherId) {
		return otherImportCount < importCount || otherImportCount == importCount && otherId < id;
	}

	/** @return 값이 없으면 null */
	static CourseSearchCursor decodeOrNull(String cursor) {
		if (cursor == null) {
			return null;
		}
		try {
			String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", -1);
			if (parts.length != 2) {
				throw invalid();
			}
			long importCount = Long.parseLong(parts[0]);
			long id = Long.parseLong(parts[1]);
			if (importCount < 0 || id < 1) {
				throw invalid();
			}
			return new CourseSearchCursor(importCount, id);
		} catch (IllegalArgumentException e) {
			throw invalid();
		}
	}

	private static BusinessException invalid() {
		return new BusinessException(ExceptionType.INVALID_CURSOR);
	}
}
