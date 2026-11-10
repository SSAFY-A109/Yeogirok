package com.ssafy.yeogirok.domain.schedule.dto;

import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.array;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.bool;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.date;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.id;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.integer;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.object;
import static com.ssafy.yeogirok.domain.schedule.dto.DraftFields.required;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.requiredInt;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * API-084 요청. 생략 C001, null·타입·형식 오류 C002, 좌표 범위 밖 C011.
 * <ul>
 *   <li>baseVersion·draftVersion·items 필수. items 는 비어도 된다</li>
 *   <li>items[]: itemId·roomPlaceId·visitDate·order·stayMinutes 필수, fixed 선택</li>
 *   <li>removeItemIds·days 선택(생략·null 은 바꾸지 않음)</li>
 *   <li>startPoint·endPoint: 생략은 그대로, null 은 해제</li>
 * </ul>
 */
public class DraftAdjustRequest {

	@JsonProperty("baseVersion")
	private JsonNode baseVersion;
	@JsonProperty("draftVersion")
	private JsonNode draftVersion;
	@JsonProperty("items")
	private JsonNode items;
	@JsonProperty("removeItemIds")
	private JsonNode removeItemIds;
	@JsonProperty("days")
	private JsonNode days;
	/** 생략과 null 을 구분하려고 키가 있었는지 따로 둔다. */
	private JsonNode startPoint;
	private boolean startPointPresent;
	private JsonNode endPoint;
	private boolean endPointPresent;

	@JsonProperty("startPoint")
	void setStartPoint(JsonNode value) {
		this.startPoint = value;
		this.startPointPresent = true;
	}

	@JsonProperty("endPoint")
	void setEndPoint(JsonNode value) {
		this.endPoint = value;
		this.endPointPresent = true;
	}

	public DraftAdjustCommand toCommand() {
		int base = requiredInt(baseVersion, "baseVersion");
		int draft = requiredInt(draftVersion, "draftVersion");
		JsonNode array = array(items, "items");
		List<DraftAdjustCommand.Item> parsed = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) {
			String path = "items[" + i + "]";
			JsonNode item = object(array.get(i), path);
			JsonNode fixed = item.get("fixed");
			parsed.add(new DraftAdjustCommand.Item(
				integer(required(item, "itemId", path), path + ".itemId"),
				id(required(item, "roomPlaceId", path), path + ".roomPlaceId"),
				date(required(item, "visitDate", path), path + ".visitDate"),
				integer(required(item, "order", path), path + ".order"),
				integer(required(item, "stayMinutes", path), path + ".stayMinutes"),
				isAbsent(fixed) ? null : bool(fixed, path + ".fixed")));
		}
		List<Integer> removes = new ArrayList<>();
		if (!isAbsent(removeItemIds)) {
			JsonNode removeArray = array(removeItemIds, "removeItemIds");
			for (int i = 0; i < removeArray.size(); i++) {
				removes.add(integer(removeArray.get(i), "removeItemIds[" + i + "]"));
			}
		}
		return new DraftAdjustCommand(base, draft, parsed, removes,
			isAbsent(days) ? null : DraftFields.days(days, "days"),
			startPointPresent ? new DraftAdjustCommand.PointUpdate(DraftFields.point(startPoint, "startPoint")) : null,
			endPointPresent ? new DraftAdjustCommand.PointUpdate(DraftFields.point(endPoint, "endPoint")) : null);
	}
}
