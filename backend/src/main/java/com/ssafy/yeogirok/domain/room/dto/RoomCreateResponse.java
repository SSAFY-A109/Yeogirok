package com.ssafy.yeogirok.domain.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** API-021 응답. */
public record RoomCreateResponse(Long id, Integer version, @JsonProperty("isCreator") boolean isCreator) {
}
