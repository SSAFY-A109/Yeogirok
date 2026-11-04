package com.ssafy.yeogirok.domain.statistics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-046 카테고리별 방문 분포. 공개 집계값만 담는다(개인 ID·원본 좌표·본문 없음). center 는 셀 중심이다.
 *
 * @param updatedAt 집계 시각. 조회할 때 계산하므로 응답을 만든 시각이다
 */
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

	/**
	 * @param unit      셀 체계 이름
	 * @param precision 이 zoom 의 셀 한 변
	 * @param minCount  이 수보다 적은 셀은 응답에서 뺐다
	 */
	public record Aggregation(String unit, String version, String precision, Integer minCount) {
	}

	/**
	 * @param count          셀 안 발자취 횟수(발자취마다 1)
	 * @param categoryCounts 카테고리 코드별 횟수. 여러 개 고른 발자취는 각 코드에 1씩 들어간다. 0 인 코드는 없다
	 */
	public record Cell(String cellId, Center center, long count, Map<String, Long> categoryCounts) {
	}

	public record Center(BigDecimal latitude, BigDecimal longitude) {
	}
}
