package com.ssafy.yeogirok.domain.schedule.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * API-040 요청을 읽은 값. 값의 범위(순서·체류 시간·날짜)는 서비스가 확인한다.
 *
 * @param items         바꾸거나 새로 넣을 항목. 보내지 않은 기존 항목은 그대로 둔다
 * @param removeItemIds 지울 기존 항목
 */
public record ScheduleUpdateCommand(int version, List<ItemEdit> items, List<Long> removeItemIds) {

	public ScheduleUpdateCommand {
		items = List.copyOf(items);
		removeItemIds = List.copyOf(removeItemIds);
	}

	/**
	 * @param id        기존 항목이면 ID, 새 항목이면 null
	 * @param fixed     생략하면 null(기존 항목은 그대로, 새 항목은 false)
	 */
	public record ItemEdit(Long id, Long roomPlaceId, LocalDate visitDate, int order, int stayMinutes,
		Boolean fixed) {
	}
}
