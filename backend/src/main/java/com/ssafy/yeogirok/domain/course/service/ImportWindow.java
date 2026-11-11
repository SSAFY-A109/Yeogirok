package com.ssafy.yeogirok.domain.course.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.ssafy.yeogirok.domain.course.dto.ImportCountBasis;

/**
 * 메인 집계 기간(API-015·093, 명세 {@code specs/main-api.md} 1). 장소를 가져간 시각 기준 [start, end), end 는 조회 시각(초 단위).
 * 015 다음 페이지는 커서에 담긴 end 로 다시 만들어 같은 구간을 쓴다.
 */
public record ImportWindow(Instant start, Instant end) {

	static final Duration LENGTH = Duration.ofDays(30);
	/** 기간 제한 없음(014 전체 기간 importCount). */
	public static final ImportWindow ALL = new ImportWindow(Instant.EPOCH, Instant.parse("9999-12-31T00:00:00Z"));

	public static ImportWindow endingAt(Instant end) {
		return new ImportWindow(end.minus(LENGTH), end);
	}

	public static ImportWindow now(Clock clock) {
		return endingAt(clock.instant().truncatedTo(ChronoUnit.SECONDS));
	}

	public ImportCountBasis basis() {
		return new ImportCountBasis(ImportCountBasis.TYPE, start, end);
	}
}
