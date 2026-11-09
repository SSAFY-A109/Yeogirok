package com.ssafy.yeogirok.domain.footprint.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;
import static com.ssafy.yeogirok.global.request.JsonFields.optionalString;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.location.LocationEvidence;

import tools.jackson.databind.JsonNode;

/**
 * API-041 요청. location·latitude·longitude·capturedAt 이 없거나 null 이면 C008, categoryCodes 가 없거나 null 이면 C001,
 * 타입·capturedAt 형식 오류는 C002. body 는 생략·null 허용.
 */
public class FootprintCreateRequest {

	@JsonProperty("location")
	private JsonNode location;

	@JsonProperty("body")
	private JsonNode body;

	@JsonProperty("categoryCodes")
	private JsonNode categoryCodes;

	public FootprintCreateCommand toCommand() {
		LocationEvidence evidence = LocationEvidence.read(location, false);
		return new FootprintCreateCommand(evidence.latitude(), evidence.longitude(), evidence.capturedAt(),
			optionalString(body, "body"), categoryCodes());
	}

	private List<String> categoryCodes() {
		if (isAbsent(categoryCodes)) {
			throw missing("categoryCodes");
		}
		List<String> codes = new ArrayList<>();
		for (int i = 0; categoryCodes.isArray() && i < categoryCodes.size(); i++) {
			codes.add(categoryCodes.get(i).isString() ? categoryCodes.get(i).stringValue() : null);
		}
		if (!categoryCodes.isArray() || codes.contains(null)) {
			throw invalidFormat("categoryCodes", "문자열 배열이어야 합니다.");
		}
		return codes;
	}
}
