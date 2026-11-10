package com.ssafy.yeogirok.domain.schedule.dto;

import java.time.LocalDate;
import java.util.List;

import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;

/**
 * API-084 요청을 읽은 값. 바꿀 항목만 담는다. 값의 범위는 서비스가 확인한다.
 *
 * @param items         바꿀 항목. 보내지 않은 항목은 그대로 둔다. 미배치 항목을 보내면 그 날짜에 배치한다
 * @param removeItemIds 일정에서 빼 미배치로 둘 항목
 * @param days          일차 조건을 바꿀 때만. null 이면 그대로
 * @param startPoint    null 이면 그대로, {@code PointUpdate(null)} 이면 해제
 * @param endPoint      null 이면 그대로, {@code PointUpdate(null)} 이면 해제
 */
public record DraftAdjustCommand(int baseVersion, int draftVersion, List<Item> items, List<Integer> removeItemIds,
	List<DayOption> days, PointUpdate startPoint, PointUpdate endPoint) {

	public DraftAdjustCommand {
		items = List.copyOf(items);
		removeItemIds = List.copyOf(removeItemIds);
		days = days == null ? null : List.copyOf(days);
	}

	/** @param fixed 생략하면 null(지금 값 그대로) */
	public record Item(int itemId, Long roomPlaceId, LocalDate visitDate, int order, int stayMinutes, Boolean fixed) {
	}

	/** 지점을 바꾼다. point 가 null 이면 지점을 없앤다. */
	public record PointUpdate(NamedPoint point) {
	}
}
