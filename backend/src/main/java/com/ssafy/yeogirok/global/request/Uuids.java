package com.ssafy.yeogirok.global.request;

import java.util.regex.Pattern;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 서버가 발급한 UUID 식별자(uploadId·materialId 등) 경로 값 검사. 서버는 소문자로만 만든다. */
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
}
