package com.ssafy.yeogirok.domain.schedule.dto;

/**
 * API-040 응답.
 *
 * @param schedule           항목을 모두 지우고 일차 조건도 없으면 null
 * @param version            저장한 뒤의 여행방 version
 * @param needsRecalculation 여행방의 일정 재계산 필요 표시(이 요청은 바꾸지 않는다)
 */
public record ScheduleUpdateResponse(ScheduleView schedule, Integer version, boolean needsRecalculation) {
}
