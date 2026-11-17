package com.ssafy.yeogirok.domain.statistics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** API-046 카테고리별 방문 분포. 공개 집계값만 담는다. updatedAt 은 응답을 만든 시각이다. */
public record VisitStatisticsResponse(
	String metric,
	Period period,
	Aggregation aggregation,
	List<Cell> cells,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant updatedAt
) {

	/** 요청한 기간. 생략했으면 null. */
	public record Period(LocalDate from, LocalDate to) {
	}

	/** precision 은 이 zoom 의 셀 한 변. 전국 보기(unit PROVINCE)는 "province". */
	public record Aggregation(String unit, String version, String precision, Integer minCount) {
	}

	/**
	 * categoryCounts 는 여러 개 고른 발자취를 각 코드에 1씩 센다. 0 인 코드는 없다.
	 * regionName 은 전국 보기의 도 이름("서울·인천·경기" 등)이다. 격자 칸에는 없어 예전 응답과 모양이 같다.
	 */
	public record Cell(String cellId, Center center, long count, Map<String, Long> categoryCounts,
		@JsonInclude(JsonInclude.Include.NON_NULL) String regionName) {
	}

	public record Center(BigDecimal latitude, BigDecimal longitude) {
	}
}
