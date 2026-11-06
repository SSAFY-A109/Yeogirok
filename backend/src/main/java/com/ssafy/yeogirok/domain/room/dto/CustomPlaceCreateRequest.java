package com.ssafy.yeogirok.domain.room.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;
import static com.ssafy.yeogirok.global.request.JsonFields.optionalString;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.Coordinates;

import tools.jackson.databind.JsonNode;

/**
 * API-031 요청.
 * <ul>
 *   <li>name: 생략·null C001, 문자열 아님 C002, 앞뒤 공백을 지운 뒤 비었거나 255자 초과 P004(내부 공백은 유지)</li>
 *   <li>latitude·longitude: 생략·null C001, 숫자 아님 C002, 범위 밖 C011</li>
 *   <li>address: 선택. 문자열 아님 C002, 앞뒤 공백을 지우고 비면 null</li>
 * </ul>
 */
public class CustomPlaceCreateRequest {

	@JsonProperty("name")
	private JsonNode name;

	@JsonProperty("latitude")
	private JsonNode latitude;

	@JsonProperty("longitude")
	private JsonNode longitude;

	@JsonProperty("address")
	private JsonNode address;

	public CustomPlaceCreateCommand toCommand() {
		String nameValue = RoomRequestFields.requiredText(name, "name");
		BigDecimal lat = coordinate(latitude, "latitude");
		BigDecimal lng = coordinate(longitude, "longitude");
		Coordinates.checkRange(lat, lng);
		String addressValue = optionalString(address, "address");
		return new CustomPlaceCreateCommand(normalizeName(nameValue), lat, lng,
			addressValue == null || addressValue.isBlank() ? null : addressValue.strip());
	}

	private static String normalizeName(String value) {
		String trimmed = value.strip();
		// DB char_length 와 같게 코드 포인트로 센다.
		int length = trimmed.codePointCount(0, trimmed.length());
		if (length == 0 || length > Place.NAME_MAX_LENGTH) {
			throw new BusinessException(ExceptionType.CUSTOM_PLACE_NAME_INVALID);
		}
		return trimmed;
	}

	private static BigDecimal coordinate(JsonNode value, String field) {
		if (isAbsent(value)) {
			throw missing(field);
		}
		if (!value.isNumber()) {
			throw invalidFormat(field, "숫자여야 합니다.");
		}
		return Coordinates.toDecimal(value);
	}
}
