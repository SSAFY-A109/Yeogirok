package com.ssafy.yeogirok.domain.footprint.dto;

/** API-045 응답. deleted 는 항상 true. */
public record FootprintDeleteResponse(boolean deleted, StatisticsStatus statisticsStatus) {

	public static FootprintDeleteResponse applied() {
		return new FootprintDeleteResponse(true, StatisticsStatus.APPLIED);
	}
}
