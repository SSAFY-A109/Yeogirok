package com.ssafy.yeogirok.domain.tip.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.location.LocationEvidence;

import tools.jackson.databind.JsonNode;

/** API-048 요청. location 은 필드 4개 모두 필수(C008), 타입 오류 C002. */
public class TipViewRequest {

	@JsonProperty("location")
	private JsonNode location;

	public LocationEvidence toLocation() {
		return LocationEvidence.read(location, true);
	}
}
