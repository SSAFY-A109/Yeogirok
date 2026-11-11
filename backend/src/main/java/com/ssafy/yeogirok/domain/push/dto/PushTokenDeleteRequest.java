package com.ssafy.yeogirok.domain.push.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.request.JsonFields;

import tools.jackson.databind.JsonNode;

/** API-096 요청. 생략·null C001, 문자열 아님 C002, 빈 토큰 C003. */
public class PushTokenDeleteRequest {

	@JsonProperty("token")
	private JsonNode token;

	public String toToken() {
		return PushTokens.validate(JsonFields.nonNullString(token, "token"));
	}
}
