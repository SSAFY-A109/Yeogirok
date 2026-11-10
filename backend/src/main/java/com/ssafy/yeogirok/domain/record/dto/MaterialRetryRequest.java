package com.ssafy.yeogirok.domain.record.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/** API-092 요청. version 생략·null C001, 정수 아님 C002(091 과 같은 규칙). */
public class MaterialRetryRequest {

	@JsonProperty("version")
	private JsonNode version;

	public int toVersion() {
		return MaterialReviewRequest.materialVersion(version);
	}
}
