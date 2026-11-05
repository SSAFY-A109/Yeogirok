package com.ssafy.yeogirok.domain.room.dto;

/** API-094. scheduleNeedsRecalculation 은 기존 값. */
public record RoomPlaceRestoreResponse(boolean restored, Long roomPlaceId, Integer version,
	boolean scheduleNeedsRecalculation) {

	public static RoomPlaceRestoreResponse of(Long roomPlaceId, Integer version, boolean scheduleNeedsRecalculation) {
		return new RoomPlaceRestoreResponse(true, roomPlaceId, version, scheduleNeedsRecalculation);
	}
}
