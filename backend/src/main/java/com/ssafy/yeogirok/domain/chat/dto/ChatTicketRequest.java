package com.ssafy.yeogirok.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.request.JsonFields;

import tools.jackson.databind.JsonNode;

/** API-077 요청. roomId 생략·null C001, 정수 아님 C002. */
public class ChatTicketRequest {

	@JsonProperty("roomId")
	private JsonNode roomId;

	public long toRoomId() {
		return JsonFields.requiredLong(roomId, "roomId");
	}
}
