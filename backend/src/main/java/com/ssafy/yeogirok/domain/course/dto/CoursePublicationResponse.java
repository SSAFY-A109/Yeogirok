package com.ssafy.yeogirok.domain.course.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-073·075 응답. courseId 는 기록 ID, version 은 기록 version.
 *
 * @param publishedAt 미게시면 null
 */
public record CoursePublicationResponse(
	Long courseId,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant publishedAt,
	Integer version
) {

	public static CoursePublicationResponse from(TravelRecord record) {
		return new CoursePublicationResponse(record.getId(), record.getPublishedAt(), record.getVersion());
	}
}
