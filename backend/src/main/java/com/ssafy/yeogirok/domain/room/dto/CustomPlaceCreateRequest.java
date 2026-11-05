package com.ssafy.yeogirok.domain.room.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;
import static com.ssafy.yeogirok.global.request.JsonFields.optionalString;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

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

	/** room_places.latitude·longitude 의 소수 자릿수. */
	private static final int COORDINATE_SCALE = 7;
	private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
	private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

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
		BigDecimal lat = coordinate(latitude, "latitude", MAX_LATITUDE);
		BigDecimal lng = coordinate(longitude, "longitude", MAX_LONGITUDE);
		String addressValue = optionalString(address, "address");
		return new CustomPlaceCreateCommand(normalizeName(nameValue), lat, lng,
			addressValue == null || addressValue.isBlank() ? null : addressValue.strip());
	}

	private static String normalizeName(String value) {
		String trimmed = value.strip();
		// DB char_length 와 같게 코드 포인트로 센다.
		int length = trimmed.codePointCount(0, trimmed.length());
		if (length == 0 || length > RoomPlace.NAME_MAX_LENGTH) {
			throw new BusinessException(ExceptionType.CUSTOM_PLACE_NAME_INVALID);
		}
		return trimmed;
	}

	private static BigDecimal coordinate(JsonNode value, String field, BigDecimal max) {
		if (isAbsent(value)) {
			throw missing(field);
		}
		if (!value.isNumber()) {
			throw invalidFormat(field, "숫자여야 합니다.");
		}
		// 1e400 처럼 double 범위를 넘는 값은 Infinity 로 읽혀 decimalValue() 가 실패한다. 범위 밖 좌표와 같은 C011.
		if (value.isFloatingPointNumber() && !Double.isFinite(value.doubleValue())) {
			throw new BusinessException(ExceptionType.COORDINATE_OUT_OF_RANGE);
		}
		BigDecimal decimal = value.decimalValue().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP);
		if (decimal.abs().compareTo(max) > 0) {
			throw new BusinessException(ExceptionType.COORDINATE_OUT_OF_RANGE);
		}
		return decimal;
	}
}
