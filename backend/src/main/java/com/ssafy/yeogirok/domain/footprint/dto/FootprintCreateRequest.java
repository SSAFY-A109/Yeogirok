package com.ssafy.yeogirok.domain.footprint.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import tools.jackson.databind.JsonNode;

/**
 * API-041 요청 {@code { "location": {latitude, longitude, capturedAt}, "body"?: string, "categoryCodes": string[] }}.
 *
 * <p>노션 오류 기준에 맞추려고 원본 JSON 값을 받아 직접 검사한다.
 * <ul>
 *   <li>location 이나 그 안의 latitude·longitude·capturedAt 이 없거나 null → C008(현재 위치 증거 누락)</li>
 *   <li>categoryCodes 가 없거나 null → C001</li>
 *   <li>타입이 다르거나 capturedAt 형식이 {@value ApiDateTime#PATTERN} 이 아님 → C002</li>
 *   <li>body 는 생략·null 허용. 문자열이 아니면 C002</li>
 * </ul>
 */
public class FootprintCreateRequest {

	/** footprints.latitude·longitude 의 소수 자릿수. */
	private static final int COORDINATE_SCALE = 7;
	private static final DateTimeFormatter CAPTURED_AT_FORMAT = DateTimeFormatter.ofPattern(ApiDateTime.PATTERN);

	private JsonNode location;
	private JsonNode body;
	private JsonNode categoryCodes;

	@JsonSetter("location")
	void setLocation(JsonNode location) {
		this.location = location;
	}

	@JsonSetter("body")
	void setBody(JsonNode body) {
		this.body = body;
	}

	@JsonSetter("categoryCodes")
	void setCategoryCodes(JsonNode categoryCodes) {
		this.categoryCodes = categoryCodes;
	}

	public FootprintCreateCommand toCommand() {
		JsonNode evidence = requireLocation();
		BigDecimal latitude = coordinate(evidence, "latitude");
		BigDecimal longitude = coordinate(evidence, "longitude");
		Instant capturedAt = capturedAt(evidence);
		return new FootprintCreateCommand(latitude, longitude, capturedAt, body(), categoryCodes());
	}

	private JsonNode requireLocation() {
		if (isAbsent(location)) {
			throw evidenceMissing("location");
		}
		if (!location.isObject()) {
			throw invalidFormat("location", "객체여야 합니다.");
		}
		return location;
	}

	private static BigDecimal coordinate(JsonNode evidence, String field) {
		JsonNode value = evidence.get(field);
		if (isAbsent(value)) {
			throw evidenceMissing("location." + field);
		}
		if (!value.isNumber()) {
			throw invalidFormat("location." + field, "숫자여야 합니다.");
		}
		return value.decimalValue().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
	}

	private static Instant capturedAt(JsonNode evidence) {
		JsonNode value = evidence.get("capturedAt");
		if (isAbsent(value)) {
			throw evidenceMissing("location.capturedAt");
		}
		if (!value.isString()) {
			throw invalidFormat("location.capturedAt", "문자열이어야 합니다.");
		}
		try {
			return OffsetDateTime.parse(value.stringValue(), CAPTURED_AT_FORMAT).toInstant();
		} catch (DateTimeParseException e) {
			throw invalidFormat("location.capturedAt", "형식은 YYYY-MM-DD HH:mm:ss+09 입니다.");
		}
	}

	private String body() {
		if (isAbsent(body)) {
			return null;
		}
		if (!body.isString()) {
			throw invalidFormat("body", "문자열이어야 합니다.");
		}
		return body.stringValue();
	}

	private List<String> categoryCodes() {
		if (isAbsent(categoryCodes)) {
			throw new BusinessException(ExceptionType.REQUIRED_VALUE_MISSING, "categoryCodes: 값이 필요합니다.");
		}
		if (!categoryCodes.isArray()) {
			throw invalidFormat("categoryCodes", "문자열 배열이어야 합니다.");
		}
		List<String> codes = new ArrayList<>(categoryCodes.size());
		for (int i = 0; i < categoryCodes.size(); i++) {
			JsonNode code = categoryCodes.get(i);
			if (code == null || !code.isString()) {
				throw invalidFormat("categoryCodes", "문자열 배열이어야 합니다.");
			}
			codes.add(code.stringValue());
		}
		return codes;
	}

	private static boolean isAbsent(JsonNode value) {
		return value == null || value.isNull() || value.isMissingNode();
	}

	private static BusinessException evidenceMissing(String field) {
		return new BusinessException(ExceptionType.LOCATION_EVIDENCE_MISSING, field + ": 현재 위치 증거가 필요합니다.");
	}

	private static BusinessException invalidFormat(String field, String reason) {
		return new BusinessException(ExceptionType.INVALID_VALUE_FORMAT, field + ": " + reason);
	}
}
