package com.ssafy.yeogirok.domain.record.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** API-070 응답. status 는 저장 후 기록 상태. */
public record StoryEditResponse(
	Long storyId,
	String body,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant updatedAt,
	RecordStatus status
) {
}
