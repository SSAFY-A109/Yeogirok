package com.ssafy.yeogirok.domain.record.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * API-066. 값 검사를 마친 요청. draftKey 가 있으면 AI 초안 반영이고 draftVersion 이 함께 있다(명세
 * {@code specs/record-api-2.md} 4-9). 없으면 1차 수동 추가다.
 */
public record VisitAddCommand(int version, String draftKey, Integer draftVersion, List<Item> items) {

	/** 수동 추가. */
	public VisitAddCommand(int version, List<Item> items) {
		this(version, null, null, items);
	}

	public boolean fromDraft() {
		return draftKey != null;
	}

	/**
	 * @param order  그 날짜 안의 위치(1부터). 겹치면 기존 방문을 뒤로 민다
	 * @param itemId        반영하는 초안 항목. 직접 추가한 항목이면 null
	 * @param plannedItemId 연결할 참고 계획 항목. 없으면 null
	 */
	public record Item(VisitPlaceInput place, LocalDate visitDate, Instant visitedAt, int order, Integer itemId,
		Long plannedItemId) {

		public Item(VisitPlaceInput place, LocalDate visitDate, Instant visitedAt, int order) {
			this(place, visitDate, visitedAt, order, null, null);
		}

		public Item(VisitPlaceInput place, LocalDate visitDate, Instant visitedAt, int order, Integer itemId) {
			this(place, visitDate, visitedAt, order, itemId, null);
		}
	}
}
