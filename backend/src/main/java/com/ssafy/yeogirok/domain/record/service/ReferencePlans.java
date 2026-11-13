package com.ssafy.yeogirok.domain.record.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.record.dto.PlannedSchedule;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleReader;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleViews;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

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

	/**
	 * 실제 방문에 연결하려는 계획 항목이 이 기록의 사본에 있는지(API-066·067). 사본이 없거나 없는 항목이면 C003. 한 항목을 여러
	 * 방문에 연결하는 것은 막지 않는다(같은 곳 재방문, 067 에서 연결을 옮길 때 순서 문제 — 명세 {@code specs/record-plan.md} 5절 3).
	 *
	 * @param plannedItemIds null(연결 안 함·해제)은 건너뛴다
	 */
	public void checkLinkable(TravelRecord record, Collection<Long> plannedItemIds) {
		Set<Long> wanted = new HashSet<>(plannedItemIds);
		wanted.remove(null);
		if (wanted.isEmpty()) {
			return;
		}
		PlannedSchedule plan = read(record);
		if (plan == null || !plan.itemIds().containsAll(wanted)) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "plannedItemId: 이 기록의 참고 계획에 없는 항목입니다.");
		}
	}

	/** @return 사본이 없으면 null */
	public PlannedSchedule read(TravelRecord record) {
		Map<String, Object> plan = record.getReferencePlan();
		return plan == null ? null : jsonMapper.convertValue(plan, PlannedSchedule.class);
	}
}
