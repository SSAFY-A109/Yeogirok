package com.ssafy.yeogirok.domain.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** API-025 응답. 승인 절차가 없어 membershipStatus 는 항상 JOINED 다. */
public record RoomJoinResponse(Long roomId, String membershipStatus, @JsonProperty("isCreator") boolean isCreator) {

	public static final String JOINED = "JOINED";

	public static RoomJoinResponse joined(Long roomId, boolean isCreator) {
		return new RoomJoinResponse(roomId, JOINED, isCreator);
	}
}
