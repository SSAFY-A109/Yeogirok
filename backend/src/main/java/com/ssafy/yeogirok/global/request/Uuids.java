package com.ssafy.yeogirok.global.request;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/** 서버가 발급한 UUID 식별자(uploadId·materialId 등) 경로·요청 값 검사. 서버는 소문자로만 만든다. */
public final class Uuids {

	private static final Pattern LOWERCASE_UUID =
		Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private Uuids() {
	}

	/** @throws BusinessException 소문자 UUID 형식이 아니면 C002 */
	public static String require(String value, String field) {
		if (value == null || !LOWERCASE_UUID.matcher(value).matches()) {
			throw JsonFields.invalidFormat(field, "UUID 여야 합니다.");
		}
		return value;
	}

	/** 값이 있다고 확인한 뒤 부른다. 배열이 아니거나 UUID 문자열이 아닌 원소가 있으면 C002. */
	public static List<String> requireList(JsonNode value, String field) {
		if (!value.isArray()) {
			throw JsonFields.invalidFormat(field, "UUID 문자열 배열이어야 합니다.");
		}
		List<String> ids = new ArrayList<>();
		for (JsonNode id : value) {
			ids.add(require(id.isString() ? id.stringValue() : null, field));
		}
		return ids;
	}

	/** 생략이면 null(바꾸지 않음), 명시한 null C001, 그 외는 {@link #requireList}(부분 수정 요청용). */
	public static List<String> optionalNonNullList(JsonNode value, String field) {
		if (value == null) {
			return null;
		}
		if (value.isNull()) {
			throw JsonFields.missing(field);
		}
		return requireList(value, field);
	}
}
