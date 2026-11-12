package com.ssafy.yeogirok.domain.record.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * 기록의 참고 계획 사본(노션 057·059 plannedSchedule). {@code travel_records.reference_plan} 에 응답 모양 그대로 저장한다.
 * {@code items[].place} 는 노션 ScheduleDay 에 없는 칸이다(명세 {@code specs/record-plan.md} 2절·5절 1).
 *
 * @param roomId  계획을 가져온 여행방
 * @param version 복사한 순간의 여행방 version
 */
public record PlannedSchedule(Long roomId, int version, List<Day> days) {

	public record Day(
		LocalDate date,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
		List<Item> items,
		List<ScheduleView.Leg> legs,
		List<String> warnings
	) {
	}

	/** @param id 일정 항목 ID(schedule_items.id). 실제 방문의 plannedItemId 가 가리킨다 */
	public record Item(
		Long id,
		Long roomPlaceId,
		int order,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant arrivalAt,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant departureAt,
		int stayMinutes,
		boolean fixed,
		RecordPlaceView place
	) {
	}

	/** 사본 안의 모든 일정 항목 ID(방문 순서대로). */
	public List<Long> itemIds() {
		return days.stream().flatMap(day -> day.items().stream()).map(Item::id).toList();
	}

	/** @param places 후보 ID → 후보(뺀 후보 포함). 항목이 가리키는 후보가 없으면 place 는 null */
	public static PlannedSchedule of(Long roomId, int version, ScheduleView schedule, Map<Long, RoomPlace> places) {
		List<Day> days = schedule.days().stream()
			.map(day -> new Day(day.date(), day.startTime(), day.endTime(),
				day.items().stream().map(item -> {
					RoomPlace roomPlace = places.get(item.roomPlaceId());
					return new Item(item.id(), item.roomPlaceId(), item.order(), item.arrivalAt(), item.departureAt(),
						item.stayMinutes(), item.fixed(),
						roomPlace == null ? null : RecordPlaceView.from(roomPlace.getPlace()));
				}).toList(),
				day.legs(), day.warnings()))
			.toList();
		return new PlannedSchedule(roomId, version, days);
	}
}
