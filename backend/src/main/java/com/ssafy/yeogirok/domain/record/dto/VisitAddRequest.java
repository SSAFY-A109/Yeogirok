package com.ssafy.yeogirok.domain.record.dto;

import static com.ssafy.yeogirok.domain.record.dto.VisitRequestFields.reject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.JsonFields;
import com.ssafy.yeogirok.global.request.Uuids;

import tools.jackson.databind.JsonNode;

/**
 * API-066 요청(수동 추가 + AI 초안 반영, 명세 {@code specs/record-api-2.md} 4-9).
 * <ul>
 *   <li>version 생략 C001, null·정수 아님 C002. items 생략 C001, 배열 아님 C002, 빈 배열·50개 초과 C003</li>
 *   <li>draftKey 는 UUID(C002). 있으면 draftVersion 필수(C001). draftKey 없이 draftVersion·itemId 가 오면 C003.
 *   itemId 는 1 이상 정수(정수 아님 C002, 1 미만 C003)</li>
 *   <li>story·storyDraft 값은 C003. plannedItemId 는 계획 사본 전이라 값이 오면 C003, null 은 통과(명세 5절 8·12)</li>
 *   <li>장소: placeRef·customPlace 둘 다 C003, 둘 다 없으면 RE018</li>
 *   <li>visitDate·visitedAt·order: 명세 1-6, order 는 필수·1 이상</li>
 * </ul>
 */
public class VisitAddRequest {

	/** items 최대 개수(PR #77 리뷰, 11/10 사용자 결정). */
	static final int MAX_ITEMS = 50;

	@JsonProperty("version")
	private JsonNode version;

	@JsonProperty("draftKey")
	private JsonNode draftKey;

	@JsonProperty("draftVersion")
	private JsonNode draftVersion;

	@JsonProperty("items")
	private JsonNode items;

	public VisitAddCommand toCommand() {
		int versionValue = JsonFields.requiredInt(version, "version");
		String key = JsonFields.isAbsent(draftKey) ? null
			: Uuids.require(JsonFields.nonNullString(draftKey, "draftKey"), "draftKey");
		Integer draftVersionValue = null;
		if (key != null) {
			draftVersionValue = VisitRequestFields.requiredInt(draftVersion, "draftVersion");
		} else {
			reject(draftVersion, "draftVersion");
		}
		List<VisitAddCommand.Item> parsed = new ArrayList<>();
		for (JsonNode item : VisitRequestFields.requiredArray(items, "items", false, MAX_ITEMS)) {
			parsed.add(item(VisitRequestFields.requiredObject(item, "items[]"), key != null));
		}
		return new VisitAddCommand(versionValue, key, draftVersionValue, parsed);
	}

	private static VisitAddCommand.Item item(JsonNode item, boolean fromDraft) {
		Integer itemId = itemId(item.get("itemId"), fromDraft);
		reject(item.get("story"), "story");
		reject(item.get("storyDraft"), "storyDraft");
		reject(item.get("plannedItemId"), "plannedItemId");
		VisitPlaceInput place = VisitRequestFields.place(item.get("placeRef"), item.get("customPlace"));
		LocalDate visitDate = VisitRequestFields.date(item.get("visitDate"), "visitDate");
		OffsetDateTime visitedAt = VisitRequestFields.dateTime(item.get("visitedAt"), "visitedAt");
		int order = VisitRequestFields.order(item.get("order"), true);
		if (place == null) {
			throw new BusinessException(ExceptionType.VISIT_PLACE_MISSING);
		}
		VisitRequestFields.checkSameDate(visitDate, visitedAt);
		return new VisitAddCommand.Item(place, visitDate, visitedAt.toInstant(), order, itemId);
	}

	/** 생략·null 이면 직접 추가한 항목. draftKey 없이 오면 C003. */
	private static Integer itemId(JsonNode value, boolean fromDraft) {
		if (JsonFields.isAbsent(value)) {
			return null;
		}
		if (!fromDraft) {
			throw VisitRequestFields.notAllowed("itemId: draftKey 없이 보낼 수 없습니다.");
		}
		int itemId = VisitRequestFields.requiredInt(value, "itemId");
		if (itemId < 1) {
			throw VisitRequestFields.notAllowed("itemId: 1 이상이어야 합니다.");
		}
		return itemId;
	}
}
