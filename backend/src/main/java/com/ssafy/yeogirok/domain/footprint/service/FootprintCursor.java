package com.ssafy.yeogirok.domain.footprint.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * API-042 목록 커서. 마지막 항목의 (visitedAt, id) 를 담는다. 앱은 내용을 해석하지 않고 그대로 돌려보낸다.
 *
 * <p>형식: base64url("{visitedAt ISO-8601}|{id}"). 해석할 수 없으면 C006.
 */
record FootprintCursor(Instant visitedAt, Long id) {

	private static final String SEPARATOR = "|";

	String encode() {
		String raw = visitedAt + SEPARATOR + id;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/** @return 값이 없으면 null */
	static FootprintCursor decodeOrNull(String cursor) {
		if (cursor == null) {
			return null;
		}
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			int separator = raw.indexOf(SEPARATOR);
			if (separator < 0) {
				throw invalid();
			}
			Instant visitedAt = Instant.parse(raw.substring(0, separator));
			long id = Long.parseLong(raw.substring(separator + 1));
			if (id < 1) {
				throw invalid();
			}
			return new FootprintCursor(visitedAt, id);
		} catch (IllegalArgumentException | DateTimeParseException e) {
			throw invalid();
		}
	}

	private static BusinessException invalid() {
		return new BusinessException(ExceptionType.INVALID_CURSOR);
	}
}
