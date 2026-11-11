package com.ssafy.yeogirok.domain.course.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * 메인 집계 기준(API-015·093 basis). periodStart 포함, periodEnd 미포함. 시각은 공통 형식(노션 예시의 ISO 형식이 아님, 명세
 * main-api.md 5절 12).
 */
public record ImportCountBasis(
	String type,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant periodStart,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant periodEnd
) {

	public static final String TYPE = "IMPORT_COUNT_30D";
}
