package com.ssafy.yeogirok.domain.schedule.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-038 일정 초안. 초안은 roomId·draftKey 로 식별하고 별도 id 를 두지 않는다(명세 본문). 항목은 초안 안의 itemId 로
 * 구별한다. 일차·구간·경고 모양은 확정 일정(API-036)과 같다.
 *
 * @param status           RUNNING(처음 만드는 중·다시 계산하는 중), SUCCEEDED, FAILED(처음 만들기 실패)
 * @param schedule         계산한 내용이 없으면(처음 만드는 중·실패) null. 다시 계산하는 중이면 마지막 성공 내용
 * @param travelDataSource 이동 시간을 받은 곳(예: KAKAO_CAR). 받은 구간이 없으면 null
 * @param error            처음 만들기에 실패했을 때만 {code, message}
 */
public record DraftResponse(JobStatus status, int baseVersion, int draftVersion, Conditions conditions,
	Schedule schedule, TransportMode transportMode, String travelDataSource, JobError error) {

	public record Conditions(List<DayCondition> days, ScheduleView.Point startPoint, ScheduleView.Point endPoint) {
	}

	public record DayCondition(
		LocalDate date,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime endTime
	) {
	}

	public record Schedule(List<Day> days, List<Unassigned> unassignedPlaces, List<String> warnings,
		ScheduleView.Point startPoint, ScheduleView.Point endPoint) {
	}

	/** legs 의 fromItemId·toItemId 는 itemId 다. */
	public record Day(
		LocalDate date,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
		List<Item> items,
		List<ScheduleView.Leg> legs,
		List<String> warnings
	) {
	}

	public record Item(
		int itemId,
		Long roomPlaceId,
		int order,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant arrivalAt,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant departureAt,
		int stayMinutes,
		boolean fixed
	) {
	}

	/** 일정에 넣지 못한(뺀) 항목. 이유·추천 날짜는 주지 않는다(명세). */
	public record Unassigned(int itemId, Long roomPlaceId) {
	}
}
