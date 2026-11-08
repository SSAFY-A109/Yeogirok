package com.ssafy.yeogirok.global.request;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import tools.jackson.databind.JsonNode;

/**
 * 요청 DTO 의 원본 JSON 값 검사(생략 C001, null·타입 오류 C002). 생략은 Java null, 명시한 null 은 NullNode 로 들어와
 * 둘을 구분할 수 있다(Jackson 기본 바인딩은 구분하지 못하고 숫자·문자열을 서로 바꿔 받아 준다).
 */
public final class JsonFields {

	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern(ApiDateTime.PATTERN);

	private JsonFields() {
	}

	/** 생략이거나 null. */
	public static boolean isAbsent(JsonNode value) {
		return value == null || value.isNull() || value.isMissingNode();
	}

	/** 생략 C001, null·정수 아님 C002. */
	public static int requiredInt(JsonNode value, String field) {
		if (value == null) {
			throw missing(field);
		}
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw invalidFormat(field, "정수여야 합니다.");
		}
		return value.intValue();
	}

	/** 생략 C001, null·문자열 아님 C002. */
	public static String requiredString(JsonNode value, String field) {
		if (value == null) {
			throw missing(field);
		}
		if (!value.isString()) {
			throw invalidFormat(field, "문자열이어야 합니다.");
		}
		return value.stringValue();
	}

	/** 생략·null C001, 문자열 아님 C002(null 을 생략과 같게 보는 필드용). */
	public static String nonNullString(JsonNode value, String field) {
		if (isAbsent(value)) {
			throw missing(field);
		}
		return requiredString(value, field);
	}

	/** 생략·null 이면 null, 문자열 아님 C002. */
	public static String optionalString(JsonNode value, String field) {
		if (isAbsent(value)) {
			return null;
		}
		if (!value.isString()) {
			throw invalidFormat(field, "문자열이어야 합니다.");
		}
		return value.stringValue();
	}

	/** 생략·null 이면 null, 정수(long 범위) 아님 C002. */
	public static Long optionalLong(JsonNode value, String field) {
		if (isAbsent(value)) {
			return null;
		}
		if (!value.isIntegralNumber() || !value.canConvertToLong()) {
			throw invalidFormat(field, "정수여야 합니다.");
		}
		return value.longValue();
	}

	/** 값이 있다고 확인한 뒤 부른다. 문자열이 아니거나 {@code YYYY-MM-DD HH:mm:ss+09} 형식이 아니면 C002. */
	public static Instant dateTime(JsonNode value, String field) {
		try {
			if (value.isString()) {
				return OffsetDateTime.parse(value.stringValue(), DATE_TIME_FORMAT).toInstant();
			}
		} catch (DateTimeParseException ignored) {
			// 아래에서 형식 오류로 알린다
		}
		throw invalidFormat(field, "형식은 YYYY-MM-DD HH:mm:ss+09 입니다.");
	}

	public static BusinessException missing(String field) {
		return new BusinessException(ExceptionType.REQUIRED_VALUE_MISSING, field + ": 값이 필요합니다.");
	}

	public static BusinessException invalidFormat(String field, String reason) {
		return new BusinessException(ExceptionType.INVALID_VALUE_FORMAT, field + ": " + reason);
	}
}
