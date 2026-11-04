package com.ssafy.yeogirok.domain.footprint.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.requiredInt;
import static com.ssafy.yeogirok.global.request.JsonFields.requiredString;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/** API-044 요청 {@code { "version": number, "body": string }}. 둘 다 생략 C001, null·타입 오류 C002. */
public class FootprintUpdateRequest {

	@JsonProperty("version")
	private JsonNode version;

	@JsonProperty("body")
	private JsonNode body;

	public int version() {
		return requiredInt(version, "version");
	}

	/** 정리 전 원본 본문. 길이·공백 규칙은 엔티티가 적용한다. */
	public String body() {
		return requiredString(body, "body");
	}
}
