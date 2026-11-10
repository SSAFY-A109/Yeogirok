package com.ssafy.yeogirok.domain.record.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.request.JsonFields;

import tools.jackson.databind.JsonNode;

/** API-071 요청. version 생략 C001, null·정수 아님 C002. */
public class RecordCompleteRequest {

	@JsonProperty("version")
	private JsonNode version;

	public int toVersion() {
		return JsonFields.requiredInt(version, "version");
	}
}
