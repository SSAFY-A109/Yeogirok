package com.ssafy.yeogirok.domain.footprint.dto;

/** API-045 응답. deleted 는 항상 true. */
public record FootprintDeleteResponse(boolean deleted, StatisticsStatus statisticsStatus) {

	/** 삭제는 API-046 조회에 바로 반영된다. */
	public static FootprintDeleteResponse applied() {
		return new FootprintDeleteResponse(true, StatisticsStatus.APPLIED);
	}
}
