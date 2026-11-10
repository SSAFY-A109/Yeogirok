package com.ssafy.yeogirok.domain.schedule.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;

/**
 * API-037 요청을 읽은 값. 값의 범위(날짜·후보·체류 시간)는 서비스가 확인한다.
 *
 * @param roomPlaceIds 현재 담은 후보 전부
 * @param days         여행의 모든 일차 조건
 * @param stayMinutes  후보별 체류 시간(분). 없는 후보는 60분
 * @param fixedItems   지금 확정 일정에서 이어받을 항목. 같은 후보가 반복되면 항목마다 한 줄
 */
public record DraftCreateCommand(int baseVersion, List<Long> roomPlaceIds, List<DayOption> days,
	NamedPoint startPoint, NamedPoint endPoint, Map<Long, Integer> stayMinutes, List<FixedItem> fixedItems) {

	public DraftCreateCommand {
		roomPlaceIds = List.copyOf(roomPlaceIds);
		days = List.copyOf(days);
		stayMinutes = Map.copyOf(stayMinutes);
		fixedItems = List.copyOf(fixedItems);
	}

	/** @param fixed 순서 고정 여부. 고정이면 그 날짜·순서 자리에 둔다 */
	public record FixedItem(Long roomPlaceId, LocalDate visitDate, int order, int stayMinutes, boolean fixed) {
	}
}
