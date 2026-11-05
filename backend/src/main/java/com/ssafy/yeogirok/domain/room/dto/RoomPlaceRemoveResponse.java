package com.ssafy.yeogirok.domain.room.dto;

/** API-032. 복원 시간 제한이 없어 undoExpiresAt 은 항상 null 이다. scheduleNeedsRecalculation 은 기존 값. */
public record RoomPlaceRemoveResponse(boolean removed, Integer version, boolean scheduleNeedsRecalculation,
	Object undoExpiresAt) {

	public static RoomPlaceRemoveResponse of(Integer version, boolean scheduleNeedsRecalculation) {
		return new RoomPlaceRemoveResponse(true, version, scheduleNeedsRecalculation, null);
	}
}
