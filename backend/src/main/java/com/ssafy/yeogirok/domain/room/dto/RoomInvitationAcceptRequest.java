package com.ssafy.yeogirok.domain.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/** API-025 요청 {@code { "inviteToken": string }}. 생략·null C001, 문자열 아님 C002. */
public class RoomInvitationAcceptRequest {

	@JsonProperty("inviteToken")
	private JsonNode inviteToken;

	public String inviteToken() {
		return RoomRequestFields.requiredText(inviteToken, "inviteToken");
	}
}
