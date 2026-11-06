package com.ssafy.yeogirok.domain.schedule.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * 확정 일정(API-036 schedule). API-022·023·039·040 응답의 schedule 도 같은 구조다.
 *
 * @param id 방마다 일정이 하나라 여행방 ID 와 같다(별도 일정 테이블이 없다)
 */
public record ScheduleView(Long id, List<Day> days, List<Unassigned> unassignedPlaces, List<String> warnings) {

	/** 구간 중 이동 시간을 모르는 것이 있다. */
	public static final String WARNING_TRAVEL_TIME_UNAVAILABLE = "TRAVEL_TIME_UNAVAILABLE";
	/** 도착 시각을 모르는 항목이 있다(일차 시작 시각·이동 시간이 없음). */
	public static final String WARNING_ARRIVAL_TIME_UNKNOWN = "ARRIVAL_TIME_UNKNOWN";
	/** 마지막 일정(도착 지점이 있으면 도착 지점 도착)이 일차 종료 시각을 넘는다. */
	public static final String WARNING_END_TIME_EXCEEDED = "END_TIME_EXCEEDED";

	/**
	 * @param startTime·endTime 일차 조건이 없으면 null
	 */
	public record Day(
		LocalDate date,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
		Point startPoint,
		Point endPoint,
		List<Item> items,
		List<Leg> legs,
		List<String> warnings
	) {
	}

	public record Point(String name, BigDecimal latitude, BigDecimal longitude) {
	}

	public record Item(
		Long id,
		Long roomPlaceId,
		int order,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant arrivalAt,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant departureAt,
		int stayMinutes,
		boolean fixed
	) {
	}

	/**
	 * @param fromItemId 출발 지점에서 오면 null
	 * @param toItemId   도착 지점으로 가면 null
	 * @param source     이동 시간을 받은 곳. 조회하지 못했으면 시도한 곳
	 */
	public record Leg(Long fromItemId, Long toItemId, TransportMode transportMode, Integer travelMinutes,
		String source) {
	}

	public record Unassigned(Long roomPlaceId, String reason, LocalDate attemptedDate) {
	}
}
