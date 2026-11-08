package com.ssafy.yeogirok.global.location;

import static com.ssafy.yeogirok.global.request.JsonFields.dateTime;
import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;

import java.math.BigDecimal;
import java.time.Instant;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.Coordinates;

import tools.jackson.databind.JsonNode;

/**
 * 요청의 {@code location} 현재 위치 증거. 객체나 필드가 없거나 null 이면 C008, 타입·형식 오류는 C002.
 * 값 기준(범위·정확도·유효 시각)은 {@link LocationEvidencePolicy} 가 본다.
 *
 * @param accuracyMeters 정확도를 받지 않는 API(041)는 null
 */
public record LocationEvidence(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
	Instant capturedAt) {

	/** @param withAccuracy accuracyMeters 를 필수로 읽을지(AR 팁). false 면 보내도 읽지 않는다. */
	public static LocationEvidence read(JsonNode location, boolean withAccuracy) {
		if (isAbsent(location)) {
			throw missing("location");
		}
		if (!location.isObject()) {
			throw invalidFormat("location", "객체여야 합니다.");
		}
		return new LocationEvidence(coordinate(location, "latitude"), coordinate(location, "longitude"),
			withAccuracy ? accuracy(location) : null,
			dateTime(field(location, "capturedAt"), "location.capturedAt"));
	}

	private static BigDecimal coordinate(JsonNode location, String name) {
		JsonNode value = field(location, name);
		if (!value.isNumber()) {
			throw invalidFormat("location." + name, "숫자여야 합니다.");
		}
		return Coordinates.toDecimal(value);
	}

	private static BigDecimal accuracy(JsonNode location) {
		JsonNode value = field(location, "accuracyMeters");
		if (!value.isNumber() || !Double.isFinite(value.doubleValue()) || value.decimalValue().signum() < 0) {
			throw invalidFormat("location.accuracyMeters", "0 이상의 숫자여야 합니다.");
		}
		return value.decimalValue();
	}

	private static JsonNode field(JsonNode location, String name) {
		JsonNode value = location.get(name);
		if (isAbsent(value)) {
			throw missing("location." + name);
		}
		return value;
	}

	private static BusinessException missing(String field) {
		return new BusinessException(ExceptionType.LOCATION_EVIDENCE_MISSING, field + ": 현재 위치 증거가 필요합니다.");
	}
}
