package com.ssafy.yeogirok.domain.room.dto;

import java.util.List;

/**
 * API-030 검사를 끝낸 요청. 멱등키 요청 해시에도 쓴다.
 *
 * @param recordId 가져올 공개 코스(원본 기록) ID
 * @param visitIds 가져올 방문. 1개 이상, 중복 없음, 요청 순서대로 후보를 만든다
 */
public record RoomPlaceImportCommand(Long recordId, List<Long> visitIds) {

	public RoomPlaceImportCommand {
		visitIds = List.copyOf(visitIds);
	}
}
