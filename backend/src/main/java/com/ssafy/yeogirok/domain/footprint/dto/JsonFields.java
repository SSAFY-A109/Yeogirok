package com.ssafy.yeogirok.domain.footprint.dto;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/**
 * 원본 JSON 값 검사. 필드를 생략하면 Java null, 명시한 null 은 NullNode 로 들어오므로 둘을 다른 오류로 나눌 수 있다.
 * (Jackson 기본 바인딩은 둘을 구분하지 못하고 숫자·문자열을 서로 바꿔 받아 준다.)
 */
final class JsonFields {

	private JsonFields() {
	}

	/** 생략이거나 null. */
	static boolean isAbsent(JsonNode value) {
		return value == null || value.isNull() || value.isMissingNode();
	}

	/** 생략 C001, null·정수 아님 C002. */
	static int requiredInt(JsonNode value, String field) {
		if (value == null) {
			throw missing(field);
		}
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw invalidFormat(field, "정수여야 합니다.");
		}
		return value.intValue();
	}

	/** 생략 C001, null·문자열 아님 C002. */
	static String requiredString(JsonNode value, String field) {
		if (value == null) {
			throw missing(field);
		}
		if (!value.isString()) {
			throw invalidFormat(field, "문자열이어야 합니다.");
		}
		return value.stringValue();
	}

	/** 생략·null 이면 null, 문자열 아님 C002. */
	static String optionalString(JsonNode value, String field) {
		if (isAbsent(value)) {
			return null;
		}
		if (!value.isString()) {
			throw invalidFormat(field, "문자열이어야 합니다.");
		}
		return value.stringValue();
	}

	static BusinessException missing(String field) {
		return new BusinessException(ExceptionType.REQUIRED_VALUE_MISSING, field + ": 값이 필요합니다.");
	}

	static BusinessException invalidFormat(String field, String reason) {
		return new BusinessException(ExceptionType.INVALID_VALUE_FORMAT, field + ": " + reason);
	}
}
