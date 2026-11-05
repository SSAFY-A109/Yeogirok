package com.ssafy.yeogirok.domain.room.dto;

import java.time.LocalDate;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;

/**
 * API-023 요청을 읽은 값.
 *
 * @param title         null 이면 유지
 * @param datesGiven    false 면 날짜 유지. true 면 startDate·endDate 로 바꾼다(둘 다 null 이면 날짜 미정)
 * @param transportMode null 이면 유지
 */
public record RoomUpdateCommand(
	int version,
	String title,
	boolean datesGiven,
	LocalDate startDate,
	LocalDate endDate,
	TransportMode transportMode,
	boolean previewOnly
) {
}
