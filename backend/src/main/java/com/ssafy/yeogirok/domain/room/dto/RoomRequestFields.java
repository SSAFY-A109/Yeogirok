package com.ssafy.yeogirok.domain.room.dto;

import static com.ssafy.yeogirok.global.request.JsonFields.invalidFormat;
import static com.ssafy.yeogirok.global.request.JsonFields.isAbsent;
import static com.ssafy.yeogirok.global.request.JsonFields.missing;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import tools.jackson.databind.JsonNode;

/** 여행방 요청 JSON 값 읽기. 생략과 명시한 null 을 구분해야 하는 필드(API-023)가 있어 원본 JsonNode 로 받는다. */
final class RoomRequestFields {

	private RoomRequestFields() {
	}

	/** 생략·null C001, 문자열 아님 C002. */
	static String requiredText(JsonNode value, String field) {
		if (isAbsent(value)) {
			throw missing(field);
		}
		if (!value.isString()) {
			throw invalidFormat(field, "문자열이어야 합니다.");
		}
		return value.stringValue();
	}

	/** 생략·null 이면 null, 문자열 아님·날짜 형식 아님 C002. */
	static LocalDate optionalDate(JsonNode value, String field) {
		if (isAbsent(value)) {
			return null;
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

	/** 문자열 아님 C002, CAR·TRANSIT 이 아니면 C003. 생략·null 처리는 부르는 쪽이 정한다. */
	static TransportMode transportMode(JsonNode value) {
		if (!value.isString()) {
			throw invalidFormat("transportMode", "문자열이어야 합니다.");
		}
		try {
			return TransportMode.valueOf(value.stringValue());
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "transportMode: CAR 또는 TRANSIT 이어야 합니다.");
		}
	}
}
