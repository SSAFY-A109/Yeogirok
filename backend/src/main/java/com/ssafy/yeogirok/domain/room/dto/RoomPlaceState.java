package com.ssafy.yeogirok.domain.room.dto;

import java.util.Arrays;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** API-028 state. ACTIVE 는 빼지 않은 후보, REMOVED 는 뺀 후보, ALL 은 둘 다. */
public enum RoomPlaceState {
	ACTIVE,
	REMOVED,
	ALL;

	/** 생략하면 ACTIVE, 없는 값이면 C003(대소문자 구분). */
	public static RoomPlaceState parse(String value) {
		if (value == null) {
			return ACTIVE;
		}
		return Arrays.stream(values())
			.filter(state -> state.name().equals(value))
			.findFirst()
			.orElseThrow(() -> new BusinessException(ExceptionType.NOT_ALLOWED_VALUE,
				"state: ACTIVE, REMOVED, ALL 중 하나여야 합니다."));
	}
}
