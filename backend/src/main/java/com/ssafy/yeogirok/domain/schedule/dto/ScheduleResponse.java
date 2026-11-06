package com.ssafy.yeogirok.domain.schedule.dto;

/**
 * API-036 응답.
 *
 * @param schedule 일정이 없으면 null
 * @param version  여행방 version
 */
public record ScheduleResponse(ScheduleView schedule, Integer version) {
}
