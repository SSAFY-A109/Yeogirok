package com.ssafy.yeogirok.domain.footprint.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** API-044 응답. 빈 본문은 null. */
public record FootprintUpdateResponse(
	Long id,
	String body,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant updatedAt,
	Integer version
) {

	public static FootprintUpdateResponse from(Footprint footprint) {
		return new FootprintUpdateResponse(
			footprint.getId(), footprint.getBody(), footprint.getUpdatedAt(), footprint.getVersion());
	}
}
