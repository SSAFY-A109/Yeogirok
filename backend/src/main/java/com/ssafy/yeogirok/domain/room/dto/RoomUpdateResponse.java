package com.ssafy.yeogirok.domain.room.dto;

import java.time.LocalDate;
import java.util.List;

import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;

/**
 * API-023 응답.
 *
 * @param applied       previewOnly=true 면 false(아무것도 바꾸지 않았다), 저장했으면 true
 * @param affectedItems 새 날짜 밖으로 빠질(저장했으면 빠진) 일정 항목. 날짜를 줄이지 않으면 빈 배열
 * @param schedule      저장한 뒤의 일정. 미리보기이거나 일정이 없으면 null
 */
public record RoomUpdateResponse(
	Long id,
	Integer version,
	boolean applied,
	boolean scheduleNeedsRecalculation,
	List<AffectedItem> affectedItems,
	ScheduleView schedule
) {

	public static RoomUpdateResponse preview(TravelRoom room, List<AffectedItem> affectedItems) {
		return new RoomUpdateResponse(room.getId(), room.getVersion(), false, room.isScheduleNeedsRecalculation(),
			affectedItems, null);
	}

	public static RoomUpdateResponse applied(TravelRoom room, List<AffectedItem> affectedItems,
		ScheduleView schedule) {
		return new RoomUpdateResponse(room.getId(), room.getVersion(), true, room.isScheduleNeedsRecalculation(),
			affectedItems, schedule);
	}

	public record AffectedItem(Long scheduleItemId, Long roomPlaceId, String name, LocalDate visitDate) {
	}
}
