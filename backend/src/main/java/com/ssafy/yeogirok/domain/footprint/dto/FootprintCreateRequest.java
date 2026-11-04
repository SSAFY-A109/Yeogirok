package com.ssafy.yeogirok.domain.footprint.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;
import static com.ssafy.yeogirok.global.request.JsonFields.optionalString;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import tools.jackson.databind.JsonNode;

/**
 * API-041 요청. location·latitude·longitude·capturedAt 이 없거나 null 이면 C008, categoryCodes 가 없거나 null 이면 C001,
 * 타입·capturedAt 형식 오류는 C002. body 는 생략·null 허용.
 */
public class FootprintCreateRequest {

	/** footprints.latitude·longitude 의 소수 자릿수. */
	private static final int COORDINATE_SCALE = 7;
	private static final DateTimeFormatter CAPTURED_AT_FORMAT = DateTimeFormatter.ofPattern(ApiDateTime.PATTERN);

	@JsonProperty("location")
	private JsonNode location;

	@JsonProperty("body")
	private JsonNode body;

	@JsonProperty("categoryCodes")
	private JsonNode categoryCodes;

	public FootprintCreateCommand toCommand() {
		if (isAbsent(location)) {
			throw evidenceMissing("location");
		}
		if (!location.isObject()) {
			throw invalidFormat("location", "객체여야 합니다.");
		}
		return new FootprintCreateCommand(coordinate("latitude"), coordinate("longitude"), capturedAt(),
			optionalString(body, "body"), categoryCodes());
	}

	private BigDecimal coordinate(String field) {
		JsonNode value = evidence(field);
		if (!value.isNumber()) {
			throw invalidFormat("location." + field, "숫자여야 합니다.");
		}
		// 1e400 처럼 double 범위를 넘는 값은 Infinity 로 읽혀 decimalValue() 가 실패한다. 범위 밖 좌표와 같은 C011.
		if (value.isFloatingPointNumber() && !Double.isFinite(value.doubleValue())) {
			throw new BusinessException(ExceptionType.COORDINATE_OUT_OF_RANGE);
		}
		return value.decimalValue().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
	}

	private Instant capturedAt() {
		JsonNode value = evidence("capturedAt");
		try {
			if (value.isString()) {
				return OffsetDateTime.parse(value.stringValue(), CAPTURED_AT_FORMAT).toInstant();
			}
		} catch (DateTimeParseException ignored) {
			// 아래에서 형식 오류로 알린다
		}
		throw invalidFormat("location.capturedAt", "형식은 YYYY-MM-DD HH:mm:ss+09 입니다.");
	}

	private JsonNode evidence(String field) {
		JsonNode value = location.get(field);
		if (isAbsent(value)) {
			throw evidenceMissing("location." + field);
		}
		return value;
	}

	private List<String> categoryCodes() {
		if (isAbsent(categoryCodes)) {
			throw missing("categoryCodes");
		}
		List<String> codes = new ArrayList<>();
		for (int i = 0; categoryCodes.isArray() && i < categoryCodes.size(); i++) {
			codes.add(categoryCodes.get(i).isString() ? categoryCodes.get(i).stringValue() : null);
		}
		if (!categoryCodes.isArray() || codes.contains(null)) {
			throw invalidFormat("categoryCodes", "문자열 배열이어야 합니다.");
		}
		return codes;
	}

	private static BusinessException evidenceMissing(String field) {
		return new BusinessException(ExceptionType.LOCATION_EVIDENCE_MISSING, field + ": 현재 위치 증거가 필요합니다.");
	}
}
