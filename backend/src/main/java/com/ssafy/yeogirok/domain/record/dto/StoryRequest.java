package com.ssafy.yeogirok.domain.record.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * API-069·070 요청. body 생략·null C001, 문자열 아님·빈 문자열·공백만 C002. 길이 상한은 노션에 없어 두지 않는다.
 * 줄바꿈은 LF 로 맞춰 저장한다(명세 5절 9).
 */
public class StoryRequest {

	@JsonProperty("body")
	private JsonNode body;

	public String toBody() {
		if (isAbsent(body)) {
			throw missing("body");
		}
		if (!body.isString()) {
			throw invalidFormat("body", "문자열이어야 합니다.");
		}
		if (body.stringValue().isBlank()) {
			throw invalidFormat("body", "내용을 입력해야 합니다.");
		}
		return body.stringValue().replace("\r\n", "\n").replace('\r', '\n');
	}
}
