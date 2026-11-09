package com.ssafy.yeogirok.global.request;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * (시각, id) 내림차순 목록 커서(API-042 발자취·053 팁). base64url("{시각 ISO-8601}|{id}"), 해석할 수 없으면 C006.
 */
public record TimeIdCursor(Instant time, Long id) {

	private static final String SEPARATOR = "|";

	public String encode() {
		String raw = time + SEPARATOR + id;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/** @return 값이 없으면 null */
	public static TimeIdCursor decodeOrNull(String cursor) {
		if (cursor == null) {
			return null;
		}
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			int separator = raw.indexOf(SEPARATOR);
			if (separator < 0) {
				throw invalid();
			}
			Instant time = Instant.parse(raw.substring(0, separator));
			long id = Long.parseLong(raw.substring(separator + 1));
			if (id < 1) {
				throw invalid();
			}
			return new TimeIdCursor(time, id);
		} catch (IllegalArgumentException | DateTimeParseException e) {
			throw invalid();
		}
	}

	private static BusinessException invalid() {
		return new BusinessException(ExceptionType.INVALID_CURSOR);
	}
}
