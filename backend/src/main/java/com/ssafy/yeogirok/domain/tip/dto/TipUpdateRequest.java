package com.ssafy.yeogirok.domain.tip.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.optionalNonNullString;
import static com.ssafy.yeogirok.global.request.JsonFields.requiredInt;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.location.LocationEvidence;
import com.ssafy.yeogirok.global.request.Uuids;

import tools.jackson.databind.JsonNode;

/**
 * API-054 요청. 생략한 항목은 바꾸지 않는다. version 생략 C001·null/타입 C002, category·body·uploadIds 의 명시한 null C001.
 * location·spatialReference·placement 중 하나라도 보내면 재배치로 보고 셋 다 받는다(location 누락 C008, 나머지 C001).
 */
public class TipUpdateRequest {

	@JsonProperty("version")
	private JsonNode version;

	@JsonProperty("category")
	private JsonNode category;

	@JsonProperty("body")
	private JsonNode body;

	@JsonProperty("uploadIds")
	private JsonNode uploadIds;

	@JsonProperty("location")
	private JsonNode location;

	@JsonProperty("spatialReference")
	private JsonNode spatialReference;

	@JsonProperty("placement")
	private JsonNode placement;

	public TipUpdateCommand toCommand() {
		boolean relocating = location != null || spatialReference != null || placement != null;
		return new TipUpdateCommand(requiredInt(version, "version"), optionalNonNullString(category, "category"),
			optionalNonNullString(body, "body"), Uuids.optionalNonNullList(uploadIds, "uploadIds"),
			relocating ? LocationEvidence.read(location, true) : null, spatialReference, placement);
	}

	/**
	 * 요청을 읽은 값. 멱등키 요청 해시도 이 값으로 계산한다. null 인 항목은 바꾸지 않는다.
	 * @param location 재배치할 때만 값이 있다. spatialReference·placement 는 서버 시각으로 검사해야 해서 원본 JSON 그대로 넘긴다
	 */
	public record TipUpdateCommand(int version, String category, String body, List<String> uploadIds,
		LocationEvidence location, JsonNode spatialReference, JsonNode placement) {

		public boolean relocating() {
			return location != null;
		}

		public boolean hasChanges() {
			return category != null || body != null || uploadIds != null || relocating();
		}
	}
}
