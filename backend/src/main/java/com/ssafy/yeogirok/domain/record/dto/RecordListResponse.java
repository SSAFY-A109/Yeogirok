package com.ssafy.yeogirok.domain.record.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** API-058 응답. */
public record RecordListResponse(List<Item> items, String nextCursor) {

	/**
	 * @param regionName  첫 실제 방문 장소의 읍·면·동 이름, 방문이 없거나 모르면 null(명세 5절 3)
	 * @param memberCount 현재 참여자 수(탈퇴자 제외)
	 * @param placeCount  실제 방문 수
	 * @param publication 게시됐으면 값, 아니면 null
	 */
	public record Item(
		Long id,
		String title,
		Long roomId,
		RecordStatus status,
		LocalDate startDate,
		LocalDate endDate,
		String regionName,
		int memberCount,
		List<Member> members,
		int placeCount,
		RecordProgress progress,
		Publication publication
	) {
	}

	public record Member(Long userId, String nickname, String profileImageUrl) {
	}

	/** courseId 는 기록 ID, version 은 기록 version(노션 058). */
	public record Publication(
		Long courseId,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
		Instant publishedAt,
		Integer version
	) {
	}
}
