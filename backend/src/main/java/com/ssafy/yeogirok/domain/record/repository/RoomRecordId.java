package com.ssafy.yeogirok.domain.record.repository;

/**
 * 여행방과 그 방의 여행 기록 ID(방마다 하나). 여행방 목록·상세(API-020·022)의 recordIds 에 쓴다.
 *
 * @param roomId   여행방 ID
 * @param recordId 여행 기록 ID
 */
public record RoomRecordId(
	Long roomId,
	Long recordId
) {
}
