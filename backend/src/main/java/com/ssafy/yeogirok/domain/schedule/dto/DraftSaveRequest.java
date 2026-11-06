package com.ssafy.yeogirok.domain.schedule.dto;

import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.array;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.bool;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.date;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.id;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.integer;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.object;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.required;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.time;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;
import static com.ssafy.yeogirok.global.request.JsonFields.requiredInt;
import static com.ssafy.yeogirok.global.request.JsonFields.requiredString;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;

import tools.jackson.databind.JsonNode;

/**
 * API-039 요청. 생략 C001, null·타입·형식 오류 C002, 좌표 범위 밖 C011.
 * <ul>
 *   <li>draftKey·draftVersion·baseVersion·days·unassignedItemIds 필수. 배열은 비어도 된다</li>
 *   <li>startPoint·endPoint 는 키가 있어야 하고 지정하지 않으면 null</li>
 *   <li>days[]: date·startTime·endTime·items 필수. items[]: itemId·roomPlaceId·order·stayMinutes·fixed 필수</li>
 *   <li>도착·출발 시각·구간·경고 같은 계산 결과는 받지 않는다(서버가 만든다)</li>
 * </ul>
 */
public class DraftSaveRequest {

	@JsonProperty("draftKey")
	private JsonNode draftKey;
	@JsonProperty("draftVersion")
	private JsonNode draftVersion;
	@JsonProperty("baseVersion")
	private JsonNode baseVersion;
	@JsonProperty("days")
	private JsonNode days;
	@JsonProperty("unassignedItemIds")
	private JsonNode unassignedItemIds;
	@JsonProperty("startPoint")
	private JsonNode startPoint;
	@JsonProperty("endPoint")
	private JsonNode endPoint;

	public DraftSaveCommand toCommand() {
		String key = requiredString(draftKey, "draftKey");
		int draft = requiredInt(draftVersion, "draftVersion");
		int base = requiredInt(baseVersion, "baseVersion");
		List<DraftSaveCommand.Day> parsedDays = days();
		List<Integer> unassigned = new ArrayList<>();
		JsonNode array = array(unassignedItemIds, "unassignedItemIds");
		for (int i = 0; i < array.size(); i++) {
			unassigned.add(integer(array.get(i), "unassignedItemIds[" + i + "]"));
		}
		return new DraftSaveCommand(key, draft, base, parsedDays, unassigned, point(startPoint, "startPoint"),
			point(endPoint, "endPoint"));
	}

	/** 키는 있어야 한다(생략 C001). null 은 지점 없음. */
	private static NamedPoint point(JsonNode value, String field) {
		if (value == null) {
			throw missing(field);
		}
		return DraftFields.point(value, field);
	}

	private List<DraftSaveCommand.Day> days() {
		JsonNode array = array(days, "days");
		List<DraftSaveCommand.Day> result = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) {
			String path = "days[" + i + "]";
			JsonNode day = object(array.get(i), path);
			JsonNode itemsNode = array(day.get("items"), path + ".items");
			List<DraftSaveCommand.Item> items = new ArrayList<>();
			for (int j = 0; j < itemsNode.size(); j++) {
				String itemPath = path + ".items[" + j + "]";
				JsonNode item = object(itemsNode.get(j), itemPath);
				items.add(new DraftSaveCommand.Item(
					integer(required(item, "itemId", itemPath), itemPath + ".itemId"),
					id(required(item, "roomPlaceId", itemPath), itemPath + ".roomPlaceId"),
					integer(required(item, "order", itemPath), itemPath + ".order"),
					integer(required(item, "stayMinutes", itemPath), itemPath + ".stayMinutes"),
					bool(required(item, "fixed", itemPath), itemPath + ".fixed")));
			}
			result.add(new DraftSaveCommand.Day(
				date(required(day, "date", path), path + ".date"),
				time(required(day, "startTime", path), path + ".startTime"),
				time(required(day, "endTime", path), path + ".endTime"),
				items));
		}
		return result;
	}
}
