package com.ssafy.yeogirok.domain.record.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** API-058 목록 커서. base64url("{마지막 기록 id}"), 해석할 수 없으면 C006. */
record RecordCursor(Long recordId) {

	String encode() {
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(recordId.toString().getBytes(StandardCharsets.UTF_8));
	}

	/** @return 값이 없으면 null */
	static RecordCursor decodeOrNull(String cursor) {
		if (cursor == null) {
			return null;
		}
		try {
			long id = Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
			if (id < 1) {
				throw invalid();
			}
			return new RecordCursor(id);
		} catch (IllegalArgumentException e) {
			throw invalid();
		}
	}

	private static BusinessException invalid() {
		return new BusinessException(ExceptionType.INVALID_CURSOR);
	}
}
