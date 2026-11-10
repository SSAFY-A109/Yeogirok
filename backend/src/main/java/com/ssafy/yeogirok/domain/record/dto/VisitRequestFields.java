package com.ssafy.yeogirok.domain.record.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;
import static com.ssafy.yeogirok.global.request.JsonFields.optionalString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.Coordinates;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import tools.jackson.databind.JsonNode;

/** 실제 방문 요청(API-066·067) 값 읽기. 명세 {@code specs/record-api.md} 1-6·1-7. */
final class VisitRequestFields {

	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern(ApiDateTime.PATTERN);

	private VisitRequestFields() {
	}

	/** 생략·null C001, 객체 아님 C002. */
	static JsonNode requiredObject(JsonNode value, String field) {
		if (isAbsent(value)) {
			throw missing(field);
		}
		if (!value.isObject()) {
			throw invalidFormat(field, "객체여야 합니다.");
		}
		return value;
	}

	/** 생략·null C001, 배열 아님 C002, 비었으면 C003. */
	static JsonNode requiredArray(JsonNode value, String field) {
		return requiredArray(value, field, false);
	}

	/** 생략·null C001, 배열 아님 C002, allowEmpty 가 아니면 빈 배열 C003. */
	static JsonNode requiredArray(JsonNode value, String field, boolean allowEmpty) {
		if (isAbsent(value)) {
			throw missing(field);
		}
		if (!value.isArray()) {
			throw invalidFormat(field, "배열이어야 합니다.");
		}
		if (!allowEmpty && value.isEmpty()) {
			throw notAllowed(field + ": 1개 이상이어야 합니다.");
		}
		return value;
	}

	/** 생략·null C001, 정수 아님 C002. */
	static long requiredId(JsonNode value, String field) {
		if (isAbsent(value)) {
			throw missing(field);
		}
		if (!value.isIntegralNumber() || !value.canConvertToLong()) {
			throw invalidFormat(field, "정수여야 합니다.");
		}
		return value.longValue();
	}

	/** 생략·null 이면 빈 목록, 배열·정수 아님 C002. 같은 ID 는 한 번만 남긴다(보낸 순서 유지). */
	static List<Long> optionalIds(JsonNode value, String field) {
		if (isAbsent(value)) {
			return List.of();
		}
		if (!value.isArray()) {
			throw invalidFormat(field, "배열이어야 합니다.");
		}
		Set<Long> ids = new LinkedHashSet<>();
		for (JsonNode id : value) {
			ids.add(requiredId(id, field + "[]"));
		}
		return new ArrayList<>(ids);
	}

	/** 생략·null 이면 false, boolean 아님 C002. */
	static boolean optionalBoolean(JsonNode value, String field) {
		if (isAbsent(value)) {
			return false;
		}
		if (!value.isBoolean()) {
			throw invalidFormat(field, "true 또는 false 여야 합니다.");
		}
		return value.booleanValue();
	}

	/** 1차에서 받지 않는 값(초안 반영·AI 이야기·계획 연결)이 있으면 C003(명세 5절 8·12, 노션 066). */
	static void reject(JsonNode value, String field) {
		if (!isAbsent(value)) {
			throw notAllowed(field + ": 지금은 보낼 수 없는 값입니다.");
		}
	}

	/** 생략·null C001, 문자열·날짜 형식 아님 C002. */
	static LocalDate date(JsonNode value, String field) {
		if (isAbsent(value)) {
			throw missing(field);
		}
		if (value.isString()) {
			try {
				return LocalDate.parse(value.stringValue());
			} catch (DateTimeParseException ignored) {
				// 아래에서 형식 오류로 알린다
			}
		}
		throw invalidFormat(field, "형식은 YYYY-MM-DD 입니다.");
	}

	/** 생략·null C001, 형식·오프셋 오류 C002. 입력 오프셋을 남겨 날짜를 비교한다. */
	static OffsetDateTime dateTime(JsonNode value, String field) {
		if (isAbsent(value)) {
			throw missing(field);
		}
		if (value.isString()) {
			try {
				return OffsetDateTime.parse(value.stringValue(), DATE_TIME_FORMAT);
			} catch (DateTimeParseException ignored) {
				// 아래에서 형식 오류로 알린다
			}
		}
		throw invalidFormat(field, "형식은 YYYY-MM-DD HH:mm:ss+09 입니다.");
	}

	/** 입력 오프셋 기준 visitedAt 의 날짜가 visitDate 와 다르면 C012. */
	static void checkSameDate(LocalDate visitDate, OffsetDateTime visitedAt) {
		if (!visitedAt.toLocalDate().equals(visitDate)) {
			throw new BusinessException(ExceptionType.INVALID_DATE_RANGE, "visitedAt: visitDate 와 같은 날이어야 합니다.");
		}
	}

	/** 순서. 정수 아님 C002, 1 미만 C003. 생략·null 은 required 면 C001, 아니면 null. */
	static Integer order(JsonNode value, boolean required) {
		if (isAbsent(value)) {
			if (required) {
				throw missing("order");
			}
			return null;
		}
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw invalidFormat("order", "정수여야 합니다.");
		}
		if (value.intValue() < 1) {
			throw notAllowed("order: 1 이상이어야 합니다.");
		}
		return value.intValue();
	}

	/** placeRef·customPlace 중 하나. 둘 다 있으면 C003, 둘 다 없으면 null(부르는 쪽이 RE018 또는 유지를 정한다). */
	static VisitPlaceInput place(JsonNode placeRef, JsonNode customPlace) {
		boolean hasRef = !isAbsent(placeRef);
		boolean hasCustom = !isAbsent(customPlace);
		if (hasRef && hasCustom) {
			throw notAllowed("placeRef 와 customPlace 는 하나만 보낼 수 있습니다.");
		}
		if (hasRef) {
			return tourPlace(requiredObject(placeRef, "placeRef"));
		}
		return hasCustom ? customPlace(requiredObject(customPlace, "customPlace")) : null;
	}

	private static VisitPlaceInput.TourPlace tourPlace(JsonNode ref) {
		String provider = requiredText(ref.get("provider"), "placeRef.provider");
		if (!PlaceProvider.TOUR_API.name().equals(provider)) {
			throw notAllowed("placeRef.provider: TOUR_API 여야 합니다.");
		}
		String id = requiredText(ref.get("providerPlaceId"), "placeRef.providerPlaceId");
		if (id.isBlank()) {
			throw notAllowed("placeRef.providerPlaceId: 비어 있을 수 없습니다.");
		}
		return new VisitPlaceInput.TourPlace(id);
	}

	/** 이름 규칙은 API-031 직접 추가와 같다(앞뒤 공백 제거 후 1~255자, P004). */
	private static VisitPlaceInput.CustomPlace customPlace(JsonNode custom) {
		String name = requiredText(custom.get("name"), "customPlace.name").strip();
		int length = name.codePointCount(0, name.length());
		if (length == 0 || length > Place.NAME_MAX_LENGTH) {
			throw new BusinessException(ExceptionType.CUSTOM_PLACE_NAME_INVALID);
		}
		BigDecimal latitude = coordinate(custom.get("latitude"), "customPlace.latitude");
		BigDecimal longitude = coordinate(custom.get("longitude"), "customPlace.longitude");
		Coordinates.checkRange(latitude, longitude);
		String address = optionalString(custom.get("address"), "customPlace.address");
		return new VisitPlaceInput.CustomPlace(name, latitude, longitude,
			address == null || address.isBlank() ? null : address.strip());
	}

	private static String requiredText(JsonNode value, String field) {
		if (isAbsent(value)) {
			throw missing(field);
		}
		if (!value.isString()) {
			throw invalidFormat(field, "문자열이어야 합니다.");
		}
		return value.stringValue();
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

	static BusinessException notAllowed(String message) {
		return new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, message);
	}
}
