package com.ssafy.yeogirok.domain.course.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** API-014 커서. 정렬 키(가져간 방 수 내림차순, 기록 id 내림차순)의 마지막 값 base64url("{importCount}:{id}"), 해석 불가 C006. */
record CourseSearchCursor(long importCount, long id) {

	String encode() {
		return encodeParts(importCount, id);
	}

	/** 정렬상 이 커서 뒤에 오는지(가져간 방 수 내림차순, 같으면 id 내림차순). */
	boolean precedes(long otherImportCount, long otherId) {
		return otherImportCount < importCount || otherImportCount == importCount && otherId < id;
	}

	/** @return 값이 없으면 null */
	static CourseSearchCursor decodeOrNull(String cursor) {
		long[] parts = decodeParts(cursor, 2);
		return parts == null ? null : new CourseSearchCursor(parts[0], parts[1]);
	}

	/** 015 커서와 함께 쓰는 형식: base64url 로 감싼 ":" 구분 정수. */
	static String encodeParts(long... parts) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(
			Arrays.stream(parts).mapToObj(Long::toString).collect(Collectors.joining(":")).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @param count 정수 개수. 마지막 값(id)은 1 이상, 나머지는 0 이상이어야 한다
	 * @return cursor 가 null 이면 null. 형식이 틀리면 C006
	 */
	static long[] decodeParts(String cursor, int count) {
		if (cursor == null) {
			return null;
		}
		try {
			String[] texts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", -1);
			if (texts.length != count) {
				throw invalid();
			}
			long[] parts = Arrays.stream(texts).mapToLong(Long::parseLong).toArray();
			if (Arrays.stream(parts).anyMatch(part -> part < 0) || parts[count - 1] < 1) {
				throw invalid();
			}
			return parts;
		} catch (IllegalArgumentException e) {
			throw invalid();
		}
	}

	private static BusinessException invalid() {
		return new BusinessException(ExceptionType.INVALID_CURSOR);
	}
}
