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

import tools.jackson.databind.JsonNode;

/**
 * API-066 요청(1차: 수동 추가만).
 * <ul>
 *   <li>version 생략 C001, null·정수 아님 C002. items 생략 C001, 배열 아님 C002, 빈 배열 C003</li>
 *   <li>draftKey·draftVersion·items[].itemId(초안 반영), story·storyDraft, plannedItemId 값은 C003(명세 5절 8·12)</li>
 *   <li>장소: placeRef·customPlace 둘 다 C003, 둘 다 없으면 RE018</li>
 *   <li>visitDate·visitedAt·order: 명세 1-6, order 는 필수·1 이상</li>
 * </ul>
 */
public class VisitAddRequest {

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
		reject(draftKey, "draftKey");
		reject(draftVersion, "draftVersion");
		List<VisitAddCommand.Item> parsed = new ArrayList<>();
		for (JsonNode item : VisitRequestFields.requiredArray(items, "items")) {
			parsed.add(item(VisitRequestFields.requiredObject(item, "items[]")));
		}
		return new VisitAddCommand(versionValue, parsed);
	}

	private static VisitAddCommand.Item item(JsonNode item) {
		reject(item.get("itemId"), "itemId");
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
		return new VisitAddCommand.Item(place, visitDate, visitedAt.toInstant(), order);
	}
}
