package com.ssafy.yeogirok.domain.footprint.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** API-043 내 방명록 상세. 위치명·주소·본문은 없으면 null 로 내보낸다. */
public record FootprintDetailResponse(
	Long id,
	BigDecimal latitude,
	BigDecimal longitude,
	String locationName,
	String address,
	String body,
	List<String> categoryCodes,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant visitedAt,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant createdAt,
	Integer version
) {

	public static FootprintDetailResponse from(Footprint footprint) {
		return new FootprintDetailResponse(
			footprint.getId(),
			footprint.getLatitude(),
			footprint.getLongitude(),
			footprint.getLocationName(),
			footprint.getAddress(),
			footprint.getBody(),
			footprint.getCategoryCodes(),
			footprint.getVisitedAt(),
			footprint.getCreatedAt(),
			footprint.getVersion());
	}
}
