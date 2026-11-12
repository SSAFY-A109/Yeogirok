package com.ssafy.yeogirok.domain.record.dto;

import static com.ssafy.yeogirok.domain.record.dto.VisitRequestFields.notAllowed;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.request.JsonFields;

import tools.jackson.databind.JsonNode;

/**
 * API-067 요청.
 * <ul>
 *   <li>version 생략 C001, null·정수 아님 C002. items 생략 C001, 배열 아님 C002(빈 배열은 삭제만 하는 요청)</li>
 *   <li>items[].visitId 필수. 같은 방문을 두 번 보내거나 수정과 삭제에 함께 넣으면 C003</li>
 *   <li>장소는 선택(둘 다 C003, 생략하면 유지). visitDate·visitedAt 은 066 과 같은 규칙, order 는 선택·1 이상</li>
 *   <li>plannedItemId 는 생략이면 유지, null 이면 연결 해제, 값이면 변경(066 과 같은 형식 검사, 명세 {@code specs/record-plan.md} 3-3).
 *   removeVisitIds 는 정수 배열, confirmDeleteStories 는 boolean(기본 false)</li>
 *   <li>items·removeVisitIds 는 각각 최대 50개(넘으면 C003)</li>
 * </ul>
 */
public class VisitUpdateRequest {

	/** items·removeVisitIds 각각의 최대 개수(PR #77 리뷰, 11/10 사용자 결정). */
	static final int MAX_ITEMS = 50;

	@JsonProperty("version")
	private JsonNode version;

	@JsonProperty("items")
	private JsonNode items;

	@JsonProperty("removeVisitIds")
	private JsonNode removeVisitIds;

	@JsonProperty("confirmDeleteStories")
	private JsonNode confirmDeleteStories;

	public VisitUpdateCommand toCommand() {
		int versionValue = JsonFields.requiredInt(version, "version");
		List<VisitUpdateCommand.Item> parsed = new ArrayList<>();
		Set<Long> visitIds = new HashSet<>();
		for (JsonNode item : VisitRequestFields.requiredArray(items, "items", true, MAX_ITEMS)) {
			VisitUpdateCommand.Item value = item(VisitRequestFields.requiredObject(item, "items[]"));
			if (!visitIds.add(value.visitId())) {
				throw notAllowed("items: 같은 방문을 두 번 보낼 수 없습니다.");
			}
			parsed.add(value);
		}
		List<Long> removed = VisitRequestFields.optionalIds(removeVisitIds, "removeVisitIds", MAX_ITEMS);
		if (removed.stream().anyMatch(visitIds::contains)) {
			throw notAllowed("removeVisitIds: 수정하는 방문은 삭제할 수 없습니다.");
		}
		return new VisitUpdateCommand(versionValue, parsed, removed,
			VisitRequestFields.optionalBoolean(confirmDeleteStories, "confirmDeleteStories"));
	}

	private static VisitUpdateCommand.Item item(JsonNode item) {
		long visitId = VisitRequestFields.requiredId(item.get("visitId"), "visitId");
		JsonNode planned = item.get("plannedItemId");
		Long plannedItemId = VisitRequestFields.plannedItemId(planned);
		VisitPlaceInput place = VisitRequestFields.place(item.get("placeRef"), item.get("customPlace"));
		LocalDate visitDate = VisitRequestFields.date(item.get("visitDate"), "visitDate");
		OffsetDateTime visitedAt = VisitRequestFields.dateTime(item.get("visitedAt"), "visitedAt");
		Integer order = VisitRequestFields.order(item.get("order"), false);
		VisitRequestFields.checkSameDate(visitDate, visitedAt);
		return new VisitUpdateCommand.Item(visitId, place, visitDate, visitedAt.toInstant(), order, planned != null,
			plannedItemId);
	}
}
