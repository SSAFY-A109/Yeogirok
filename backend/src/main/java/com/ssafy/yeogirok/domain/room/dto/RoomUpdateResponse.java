package com.ssafy.yeogirok.domain.room.dto;

import java.time.LocalDate;
import java.util.List;

import com.ssafy.yeogirok.domain.room.entity.TravelRoom;

/**
 * API-023 응답. 일정 API 를 만들기 전이라 날짜 밖으로 빠질 일정 항목이 없어 affectedItems 는 빈 배열,
 * schedule 은 null 이다.
 *
 * @param applied previewOnly=true 면 false(아무것도 바꾸지 않았다), 저장했으면 true
 */
public record RoomUpdateResponse(
	Long id,
	Integer version,
	boolean applied,
	boolean scheduleNeedsRecalculation,
	List<AffectedItem> affectedItems,
	Object schedule
) {

	public static RoomUpdateResponse of(TravelRoom room, boolean applied) {
		return new RoomUpdateResponse(room.getId(), room.getVersion(), applied, room.isScheduleNeedsRecalculation(),
			List.of(), null);
	}

	public record AffectedItem(Long scheduleItemId, Long roomPlaceId, String name, LocalDate visitDate) {
	}
}
