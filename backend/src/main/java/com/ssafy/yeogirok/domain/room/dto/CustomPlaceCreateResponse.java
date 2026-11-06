package com.ssafy.yeogirok.domain.room.dto;

/** API-031. version 은 후보를 담은 뒤의 여행방 version 이다. */
public record CustomPlaceCreateResponse(RoomPlaceResponse roomPlace, Integer version) {
}
