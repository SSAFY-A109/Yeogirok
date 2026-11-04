package com.ssafy.yeogirok.domain.footprint.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** API-041 응답. statisticsStatus 는 항상 APPLIED({@link StatisticsStatus}). */
public record FootprintCreateResponse(
	Long id,
	BigDecimal latitude,
	BigDecimal longitude,
	String body,
	List<String> categoryCodes,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant visitedAt,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant createdAt,
	StatisticsStatus statisticsStatus
) {

	public static FootprintCreateResponse from(Footprint footprint) {
		return new FootprintCreateResponse(
			footprint.getId(),
			footprint.getLatitude(),
			footprint.getLongitude(),
			footprint.getBody(),
			footprint.getCategoryCodes(),
			footprint.getVisitedAt(),
			footprint.getCreatedAt(),
			StatisticsStatus.APPLIED);
	}
}
