package com.ssafy.yeogirok.domain.record.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * 실제 방문 하나(노션 059·067 ActualVisit). photos 는 방문 안 순서, stories 는 작성 순서.
 *
 * @param order 그 날짜 안의 순서(actual_visits.position)
 */
public record ActualVisitView(
	Long id,
	RecordPlaceView place,
	Long plannedItemId,
	LocalDate visitDate,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant visitedAt,
	int order,
	List<RecordPhotoView> photos,
	List<Story> stories
) {

	/** @param authorId 탈퇴했거나 없는 회원이면 null */
	public record Story(Long id, Long authorId, String body) {
	}
}
