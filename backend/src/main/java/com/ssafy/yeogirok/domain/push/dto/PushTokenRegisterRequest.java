package com.ssafy.yeogirok.domain.push.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.push.entity.PushPlatform;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.JsonFields;

import tools.jackson.databind.JsonNode;

/** API-095 요청. 생략·null C001, 문자열 아님 C002, 빈 토큰·지원하지 않는 플랫폼 C003. */
public class PushTokenRegisterRequest {

	@JsonProperty("token")
	private JsonNode token;

	@JsonProperty("platform")
	private JsonNode platform;

	public String toToken() {
		return PushTokens.validate(JsonFields.nonNullString(token, "token"));
	}

	public PushPlatform toPlatform() {
		String value = JsonFields.nonNullString(platform, "platform");
		for (PushPlatform candidate : PushPlatform.values()) {
			if (candidate.name().equals(value)) {
				return candidate;
			}
		}
		throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "platform: ANDROID 만 지원합니다.");
	}
}
