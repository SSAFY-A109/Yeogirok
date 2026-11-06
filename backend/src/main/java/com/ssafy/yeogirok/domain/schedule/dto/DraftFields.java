package com.ssafy.yeogirok.domain.schedule.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;
import static com.ssafy.yeogirok.global.request.JsonFields.optionalString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.global.geo.Coordinates;

import tools.jackson.databind.JsonNode;

/** 초안 요청(API-037·039·084)이 함께 쓰는 필드 읽기. 생략 C001, null·타입·형식 오류 C002, 좌표 범위 밖 C011. */
public final class DraftFields {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	private DraftFields() {
	}

	/** 필수 배열. 생략 C001, null·배열 아님 C002. */
	public static JsonNode array(JsonNode value, String field) {
		if (value == null) {
			throw missing(field);
		}
		if (!value.isArray()) {
			throw invalidFormat(field, "배열이어야 합니다.");
		}
		return value;
	}

	/** 필수 필드. 생략 C001(null 은 읽는 쪽이 C002 로 알린다). */
	public static JsonNode required(JsonNode node, String field, String path) {
		JsonNode value = node.get(field);
		if (value == null) {
			throw missing(path + "." + field);
		}
		return value;
	}

	public static JsonNode object(JsonNode node, String path) {
		if (node == null || !node.isObject()) {
			throw invalidFormat(path, "객체여야 합니다.");
		}
		return node;
	}

	public static long id(JsonNode value, String field) {
		if (!value.isIntegralNumber() || !value.canConvertToLong()) {
			throw invalidFormat(field, "정수여야 합니다.");
		}
		return value.longValue();
	}

	public static int integer(JsonNode value, String field) {
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw invalidFormat(field, "정수여야 합니다.");
		}
		return value.intValue();
	}

	public static boolean bool(JsonNode value, String field) {
		if (!value.isBoolean()) {
			throw invalidFormat(field, "boolean 이어야 합니다.");
		}
		return value.booleanValue();
	}

	public static List<Long> ids(JsonNode value, String field) {
		List<Long> ids = new ArrayList<>();
		JsonNode array = array(value, field);
		for (int i = 0; i < array.size(); i++) {
			ids.add(id(array.get(i), field + "[" + i + "]"));
		}
		return ids;
	}

	public static LocalDate date(JsonNode value, String field) {
		if (value.isString()) {
			try {
				return LocalDate.parse(value.stringValue());
			} catch (DateTimeParseException ignored) {
				// 아래에서 형식 오류로 알린다
			}
		}
		throw invalidFormat(field, "형식은 YYYY-MM-DD 입니다.");
	}

	public static LocalTime time(JsonNode value, String field) {
		if (value.isString()) {
			try {
				return LocalTime.parse(value.stringValue(), TIME);
			} catch (DateTimeParseException ignored) {
				// 아래에서 형식 오류로 알린다
			}
		}
		throw invalidFormat(field, "형식은 HH:mm:ss 입니다.");
	}

	/** 일차 조건 배열 [{date, startTime, endTime}]. 값의 범위·순서는 서비스가 확인한다. */
	public static List<DayOption> days(JsonNode value, String field) {
		JsonNode array = array(value, field);
		List<DayOption> days = new ArrayList<>();
		for (int i = 0; i < array.size(); i++) {
			String path = field + "[" + i + "]";
			JsonNode day = object(array.get(i), path);
			days.add(new DayOption(
				date(required(day, "date", path), path + ".date"),
				time(required(day, "startTime", path), path + ".startTime"),
				time(required(day, "endTime", path), path + ".endTime")));
		}
		return days;
	}

	/** 출발·도착 지점 {name: string|null, latitude, longitude}. null 이면 null. */
	public static NamedPoint point(JsonNode value, String field) {
		if (isAbsent(value)) {
			return null;
		}
		JsonNode node = object(value, field);
		BigDecimal latitude = coordinate(required(node, "latitude", field), field + ".latitude");
		BigDecimal longitude = coordinate(required(node, "longitude", field), field + ".longitude");
		Coordinates.checkRange(latitude, longitude);
		return new NamedPoint(optionalString(node.get("name"), field + ".name"), latitude, longitude);
	}

	private static BigDecimal coordinate(JsonNode value, String field) {
		if (!value.isNumber()) {
			throw invalidFormat(field, "숫자여야 합니다.");
		}
		return Coordinates.toDecimal(value);
	}
}
