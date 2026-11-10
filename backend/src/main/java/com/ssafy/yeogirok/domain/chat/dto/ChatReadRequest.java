package com.ssafy.yeogirok.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.request.JsonFields;

import tools.jackson.databind.JsonNode;

/** API-088 요청. 생략·null C001, 정수 아님 C002. 양수·같은 방 메시지인지는 서비스가 본다(C003). */
public class ChatReadRequest {

	@JsonProperty("lastReadMessageId")
	private JsonNode lastReadMessageId;

	public long toLastReadMessageId() {
		return JsonFields.requiredLong(lastReadMessageId, "lastReadMessageId");
	}
}
