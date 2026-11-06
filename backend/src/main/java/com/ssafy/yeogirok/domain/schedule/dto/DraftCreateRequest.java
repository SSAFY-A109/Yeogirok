package com.ssafy.yeogirok.domain.schedule.dto;

import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.array;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.bool;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.date;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.id;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.integer;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.object;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.required;
import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.requiredInt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * API-037 요청. 생략 C001, null·타입·형식 오류 C002, 좌표 범위 밖 C011.
 * <ul>
 *   <li>baseVersion·roomPlaceIds·days(date·startTime·endTime) 필수</li>
 *   <li>startPoint·endPoint·stayMinutes·fixedItems 선택(생략·null 허용)</li>
 *   <li>stayMinutes: 후보 ID(문자열 키) → 분(정수)</li>
 *   <li>fixedItems[]: roomPlaceId·visitDate·order·stayMinutes 필수, fixed 선택(기본 false)</li>
 *   <li>mealConstraints 는 명세가 미정이라 읽지 않는다. 식사 배치는 정한 규칙을 항상 쓴다</li>
 * </ul>
 */
public class DraftCreateRequest {

	@JsonProperty("baseVersion")
	private JsonNode baseVersion;
	@JsonProperty("roomPlaceIds")
	private JsonNode roomPlaceIds;
	@JsonProperty("days")
	private JsonNode days;
	@JsonProperty("startPoint")
	private JsonNode startPoint;
	@JsonProperty("endPoint")
	private JsonNode endPoint;
	@JsonProperty("stayMinutes")
	private JsonNode stayMinutes;
	@JsonProperty("fixedItems")
	private JsonNode fixedItems;

	public DraftCreateCommand toCommand() {
		return new DraftCreateCommand(
			requiredInt(baseVersion, "baseVersion"),
			DraftFields.ids(roomPlaceIds, "roomPlaceIds"),
			DraftFields.days(days, "days"),
			DraftFields.point(startPoint, "startPoint"),
			DraftFields.point(endPoint, "endPoint"),
			stayMinutes(),
			fixedItems());
	}

	private Map<Long, Integer> stayMinutes() {
		Map<Long, Integer> result = new LinkedHashMap<>();
		if (isAbsent(stayMinutes)) {
			return result;
		}
		JsonNode node = object(stayMinutes, "stayMinutes");
		for (Map.Entry<String, JsonNode> entry : node.properties()) {
			String field = "stayMinutes." + entry.getKey();
			long placeId;
			try {
				placeId = Long.parseLong(entry.getKey());
			} catch (NumberFormatException e) {
				throw invalidFormat(field, "키는 후보 ID(정수)여야 합니다.");
			}
			result.put(placeId, integer(entry.getValue(), field));
		}
		return result;
	}

	private List<DraftCreateCommand.FixedItem> fixedItems() {
		List<DraftCreateCommand.FixedItem> result = new ArrayList<>();
		if (isAbsent(fixedItems)) {
			return result;
		}
		JsonNode array = array(fixedItems, "fixedItems");
		for (int i = 0; i < array.size(); i++) {
			String path = "fixedItems[" + i + "]";
			JsonNode item = object(array.get(i), path);
			JsonNode fixed = item.get("fixed");
			result.add(new DraftCreateCommand.FixedItem(
				id(required(item, "roomPlaceId", path), path + ".roomPlaceId"),
				date(required(item, "visitDate", path), path + ".visitDate"),
				integer(required(item, "order", path), path + ".order"),
				integer(required(item, "stayMinutes", path), path + ".stayMinutes"),
				!isAbsent(fixed) && bool(fixed, path + ".fixed")));
		}
		return result;
	}
}
