package com.ssafy.yeogirok.domain.record.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** API-066. 값 검사를 마친 요청. */
public record VisitAddCommand(int version, List<Item> items) {

	/** @param order 그 날짜 안의 위치(1부터). 겹치면 기존 방문을 뒤로 민다 */
	public record Item(VisitPlaceInput place, LocalDate visitDate, Instant visitedAt, int order) {
	}
}
