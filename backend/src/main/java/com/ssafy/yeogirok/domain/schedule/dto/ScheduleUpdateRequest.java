package com.ssafy.yeogirok.domain.schedule.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;
import static com.ssafy.yeogirok.global.request.JsonFields.requiredInt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import tools.jackson.databind.JsonNode;

/**
 * API-040 요청. 생략 C001, null·타입·형식 오류 C002.
 * <ul>
 *   <li>version·items 필수. items 는 빈 배열이어도 된다</li>
 *   <li>items[]: roomPlaceId·visitDate(YYYY-MM-DD)·order·stayMinutes 필수, id·arrivalAt(YYYY-MM-DD HH:mm:ss+09)·
 *   fixed 선택(생략·null 허용)</li>
 *   <li>removeItemIds 선택. 생략·null 은 빈 배열</li>
 * </ul>
 */
public class ScheduleUpdateRequest {

	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern(ApiDateTime.PATTERN);

	@JsonProperty("version")
	private JsonNode version;

	@JsonProperty("items")
	private JsonNode items;

	@JsonProperty("removeItemIds")
	private JsonNode removeItemIds;

	public ScheduleUpdateCommand toCommand() {
		int versionValue = requiredInt(version, "version");
		if (items == null) {
			throw missing("items");
		}
		if (!items.isArray()) {
			throw invalidFormat("items", "배열이어야 합니다.");
		}
		List<ScheduleUpdateCommand.ItemEdit> edits = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			edits.add(item(items.get(i), "items[" + i + "]"));
		}

		List<Long> removes = new ArrayList<>();
		if (!isAbsent(removeItemIds)) {
			if (!removeItemIds.isArray()) {
				throw invalidFormat("removeItemIds", "배열이어야 합니다.");
			}
			for (int i = 0; i < removeItemIds.size(); i++) {
				removes.add(id(removeItemIds.get(i), "removeItemIds[" + i + "]"));
			}
		}
		return new ScheduleUpdateCommand(versionValue, edits, removes);
	}

	private static ScheduleUpdateCommand.ItemEdit item(JsonNode node, String path) {
		if (node == null || !node.isObject()) {
			throw invalidFormat(path, "객체여야 합니다.");
		}
		JsonNode idNode = node.get("id");
		JsonNode arrivalNode = node.get("arrivalAt");
		JsonNode fixedNode = node.get("fixed");
		Boolean fixed = null;
		if (!isAbsent(fixedNode)) {
			if (!fixedNode.isBoolean()) {
				throw invalidFormat(path + ".fixed", "boolean 이어야 합니다.");
			}
			fixed = fixedNode.booleanValue();
		}
		return new ScheduleUpdateCommand.ItemEdit(
			isAbsent(idNode) ? null : id(idNode, path + ".id"),
			id(required(node, "roomPlaceId", path), path + ".roomPlaceId"),
			date(required(node, "visitDate", path), path + ".visitDate"),
			requiredInt(node.get("order"), path + ".order"),
			isAbsent(arrivalNode) ? null : dateTime(arrivalNode, path + ".arrivalAt"),
			requiredInt(node.get("stayMinutes"), path + ".stayMinutes"),
			fixed);
	}

	/** 생략 C001. null 은 C002 로 아래에서 알린다. */
	private static JsonNode required(JsonNode node, String field, String path) {
		JsonNode value = node.get(field);
		if (value == null) {
			throw missing(path + "." + field);
		}
		return value;
	}

	private static long id(JsonNode value, String field) {
		if (!value.isIntegralNumber() || !value.canConvertToLong()) {
			throw invalidFormat(field, "정수여야 합니다.");
		}
		return value.longValue();
	}

	private static LocalDate date(JsonNode value, String field) {
		if (value.isString()) {
			try {
				return LocalDate.parse(value.stringValue());
			} catch (DateTimeParseException ignored) {
				// 아래에서 형식 오류로 알린다
			}
		}
		throw invalidFormat(field, "형식은 YYYY-MM-DD 입니다.");
	}

	private static Instant dateTime(JsonNode value, String field) {
		if (value.isString()) {
			try {
				return OffsetDateTime.parse(value.stringValue(), DATE_TIME_FORMAT).toInstant();
			} catch (DateTimeParseException ignored) {
				// 아래에서 형식 오류로 알린다
			}
		}
		throw invalidFormat(field, "형식은 YYYY-MM-DD HH:mm:ss+09 입니다.");
	}
}
