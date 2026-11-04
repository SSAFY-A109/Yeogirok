package com.ssafy.yeogirok.global.request;

import java.time.Instant;
import java.time.LocalDate;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/** from·to 날짜 조건(Asia/Seoul 달력 날짜, 양끝 포함). 둘 다 선택이고 from 이 to 보다 늦으면 C012. */
public record DateRange(LocalDate from, LocalDate to) {

	public DateRange {
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessException(ExceptionType.INVALID_DATE_RANGE, "from 이 to 보다 늦습니다.");
		}
	}

	/** from 날짜 00:00. 없으면 null. */
	public Instant startInclusive() {
		return from == null ? null : from.atStartOfDay(ApiDateTime.ZONE).toInstant();
	}

	/** to 다음 날 00:00(이 시각 미만). 없으면 null. */
	public Instant endExclusive() {
		return to == null ? null : to.plusDays(1).atStartOfDay(ApiDateTime.ZONE).toInstant();
	}
}
