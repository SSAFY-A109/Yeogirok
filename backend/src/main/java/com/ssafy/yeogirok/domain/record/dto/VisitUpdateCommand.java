package com.ssafy.yeogirok.domain.record.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** API-067. 값 검사를 마친 요청. removeVisitIds 는 중복을 뺀 값이다. */
public record VisitUpdateCommand(int version, List<Item> items, List<Long> removeVisitIds,
	boolean confirmDeleteStories) {

	/**
	 * @param place null 이면 기존 장소를 유지한다
	 * @param order null 이면 날짜가 그대로일 때 자리를 유지하고, 날짜가 바뀌면 새 날짜의 맨 뒤에 붙는다(명세 5절 7)
	 */
	public record Item(Long visitId, VisitPlaceInput place, LocalDate visitDate, Instant visitedAt, Integer order) {
	}
}
