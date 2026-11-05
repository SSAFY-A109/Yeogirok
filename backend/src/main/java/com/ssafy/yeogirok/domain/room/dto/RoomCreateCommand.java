package com.ssafy.yeogirok.domain.room.dto;

import java.time.LocalDate;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;

/** API-021 요청을 읽은 값. 멱등키 요청 해시도 이 값으로 계산한다. */
public record RoomCreateCommand(String title, LocalDate startDate, LocalDate endDate, TransportMode transportMode) {
}
