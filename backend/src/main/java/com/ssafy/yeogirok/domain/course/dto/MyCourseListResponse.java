package com.ssafy.yeogirok.domain.course.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** API-074 응답. courseId 는 기록 ID, version 은 기록 version. */
public record MyCourseListResponse(List<Item> items, String nextCursor) {

	public record Item(
		Long courseId,
		String title,
		List<PublicAuthor> authors,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
		Instant publishedAt,
		Integer version
	) {
	}
}
