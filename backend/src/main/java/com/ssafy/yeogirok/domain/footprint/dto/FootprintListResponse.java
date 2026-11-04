package com.ssafy.yeogirok.domain.footprint.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** API-042 내 발자취 목록. 다음 쪽이 없으면 nextCursor 는 null. */
public record FootprintListResponse(List<Item> items, String nextCursor) {

	public record Item(
		Long id,
		BigDecimal latitude,
		BigDecimal longitude,
		String locationName,
		String address,
		String body,
		List<String> categoryCodes,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
		Instant visitedAt
	) {

		public static Item from(Footprint footprint) {
			return new Item(
				footprint.getId(),
				footprint.getLatitude(),
				footprint.getLongitude(),
				footprint.getLocationName(),
				footprint.getAddress(),
				footprint.getBody(),
				footprint.getCategoryCodes(),
				footprint.getVisitedAt());
		}
	}
}
