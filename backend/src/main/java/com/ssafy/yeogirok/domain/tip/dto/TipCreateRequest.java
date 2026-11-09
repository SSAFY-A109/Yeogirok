package com.ssafy.yeogirok.domain.tip.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.nonNullString;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.location.LocationEvidence;
import com.ssafy.yeogirok.global.request.Uuids;

import tools.jackson.databind.JsonNode;

/**
 * API-049 요청. 형식만 본다: category·body 없음·null C001, 문자열 아님 C002, uploadIds 는 UUID 문자열 배열(생략·null 은
 * 빈 배열), location 은 C008·C002. 값 검사(종류·본문·개수·공간 참조·배치)는 서비스가 한다. visibility 는 받지 않는다.
 */
public class TipCreateRequest {

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

	public TipCreateCommand toCommand() {
		return new TipCreateCommand(nonNullString(category, "category"), nonNullString(body, "body"),
			isAbsent(uploadIds) ? List.of() : Uuids.requireList(uploadIds, "uploadIds"),
			LocationEvidence.read(location, true), spatialReference, placement);
	}

	/**
	 * 요청을 읽은 값. 멱등키 요청 해시도 이 값으로 계산한다.
	 * @param spatialReference·placement 서버 시각으로 검사해야 해서 원본 JSON 그대로 넘긴다
	 */
	public record TipCreateCommand(String category, String body, List<String> uploadIds, LocationEvidence location,
		JsonNode spatialReference, JsonNode placement) {
	}
}
