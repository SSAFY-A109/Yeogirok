package com.ssafy.yeogirok.domain.footprint.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/**
 * API-044 요청 {@code { "version": number, "body": string }}. 둘 다 필수다.
 *
 * <p>필드를 생략하면 C001, {@code null} 이거나 타입이 다르면 C002 로 나눠야 해서 원본 JSON 값을 받아 직접 검사한다
 * (Jackson 기본 설정은 생략과 null 을 구분하지 못하고, 숫자를 문자열로·문자열을 숫자로 바꿔 받아 준다).
 */
public class FootprintUpdateRequest {

	private JsonNode version;
	private boolean versionPresent;

	private JsonNode body;
	private boolean bodyPresent;

	@JsonSetter("version")
	void setVersion(JsonNode version) {
		this.version = version;
		this.versionPresent = true;
	}

	@JsonSetter("body")
	void setBody(JsonNode body) {
		this.body = body;
		this.bodyPresent = true;
	}

	/** @throws BusinessException 생략이면 C001, null·정수가 아님·범위 밖이면 C002 */
	public int version() {
		if (!versionPresent) {
			throw missing("version");
		}
		if (version == null || version.isNull() || !version.isIntegralNumber() || !version.canConvertToInt()) {
			throw invalidFormat("version", "정수여야 합니다.");
		}
		return version.intValue();
	}

	/** 정리 전 원본 본문. 길이·공백 규칙은 엔티티가 적용한다. @throws BusinessException 생략이면 C001, null·문자열이 아님이면 C002 */
	public String body() {
		if (!bodyPresent) {
			throw missing("body");
		}
		if (body == null || body.isNull() || !body.isString()) {
			throw invalidFormat("body", "문자열이어야 합니다.");
		}
		return body.stringValue();
	}

	private static BusinessException missing(String field) {
		return new BusinessException(ExceptionType.REQUIRED_VALUE_MISSING, field + ": 값이 필요합니다.");
	}

	private static BusinessException invalidFormat(String field, String reason) {
		return new BusinessException(ExceptionType.INVALID_VALUE_FORMAT, field + ": " + reason);
	}
}
