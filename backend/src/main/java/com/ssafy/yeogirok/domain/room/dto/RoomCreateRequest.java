package com.ssafy.yeogirok.domain.room.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * API-021 요청. title·transportMode 는 생략·null 이면 C001, 타입 오류 C002, 허용하지 않는 이동 수단 C003.
 * 날짜는 둘 다 보내거나 둘 다 null(미정)이다. 값 검사(제목 길이·날짜 순서·오늘 이전 출발일)는 서비스가 한다.
 */
public class RoomCreateRequest {

	@JsonProperty("title")
	private JsonNode title;

	@JsonProperty("startDate")
	private JsonNode startDate;

	@JsonProperty("endDate")
	private JsonNode endDate;

	@JsonProperty("transportMode")
	private JsonNode transportMode;

	public RoomCreateCommand toCommand() {
		String titleValue = RoomRequestFields.requiredText(title, "title");
		if (isAbsent(transportMode)) {
			throw missing("transportMode");
		}
		return new RoomCreateCommand(titleValue, RoomRequestFields.optionalDate(startDate, "startDate"),
			RoomRequestFields.optionalDate(endDate, "endDate"), RoomRequestFields.transportMode(transportMode));
	}
}
