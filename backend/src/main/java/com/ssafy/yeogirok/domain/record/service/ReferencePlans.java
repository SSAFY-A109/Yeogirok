package com.ssafy.yeogirok.domain.record.service;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.record.dto.PlannedSchedule;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleReader;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleViews;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 참고 계획 사본 만들기·읽기({@code travel_records.reference_plan} ↔ {@link PlannedSchedule}). */
@Component
@RequiredArgsConstructor
public class ReferencePlans {

	private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
	};

	private final ScheduleReader scheduleReader;
	private final JsonMapper jsonMapper;

	/**
	 * 방의 확정 일정을 지금 모양 그대로 복사한다. 뺀 후보를 가리키는 항목도 넣는다(노션 057). 방 행을 잠근 트랜잭션 안에서 부른다.
	 *
	 * @return 확정 항목이 없으면 null
	 */
	public Map<String, Object> copy(TravelRoom room) {
		ScheduleView schedule = ScheduleViews.of(room, scheduleReader.items(room.getId()));
		if (schedule == null) {
			return null;
		}
		PlannedSchedule plan = PlannedSchedule.of(room.getId(), room.getVersion(), schedule,
			scheduleReader.places(room.getId()));
		return jsonMapper.convertValue(plan, MAP);
	}

	/** @return 사본이 없으면 null */
	public PlannedSchedule read(TravelRecord record) {
		Map<String, Object> plan = record.getReferencePlan();
		return plan == null ? null : jsonMapper.convertValue(plan, PlannedSchedule.class);
	}
}
