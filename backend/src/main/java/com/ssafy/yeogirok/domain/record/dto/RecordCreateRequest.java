package com.ssafy.yeogirok.domain.record.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.optionalString;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * API-057 요청. title 은 선택: 생략·null 이면 여행방 제목을 쓴다(명세 5절 1). 문자열 아님 C002,
 * 앞뒤 공백을 지운 뒤 비었거나 200자 초과면 C003(여행방 제목과 같은 규칙).
 */
public class RecordCreateRequest {

	@JsonProperty("title")
	private JsonNode title;

	/** @return 정리한 제목, 생략이면 null */
	public String toTitle() {
		String value = optionalString(title, "title");
		return value == null ? null : RecordTitle.normalize(value);
	}
}
