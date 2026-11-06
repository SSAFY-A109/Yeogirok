package com.ssafy.yeogirok.domain.schedule.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;

/**
 * API-039 요청을 읽은 값. 변경분이 아니라 편집을 마친 전체 상태다. 값의 범위는 서비스가 확인한다.
 *
 * @param days              모든 여행 날짜의 최종 배치
 * @param unassignedItemIds 일정에서 뺄 초안 항목 전체
 */
public record DraftSaveCommand(String draftKey, int draftVersion, int baseVersion, List<Day> days,
	List<Integer> unassignedItemIds, NamedPoint startPoint, NamedPoint endPoint) {

	public DraftSaveCommand {
		days = List.copyOf(days);
		unassignedItemIds = List.copyOf(unassignedItemIds);
	}

	public record Day(LocalDate date, LocalTime startTime, LocalTime endTime, List<Item> items) {

		public Day {
			items = List.copyOf(items);
		}
	}

	/** @param itemId 원본 초안의 항목 ID */
	public record Item(int itemId, Long roomPlaceId, int order, int stayMinutes, boolean fixed) {
	}
}
