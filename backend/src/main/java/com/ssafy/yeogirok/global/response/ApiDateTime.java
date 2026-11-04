package com.ssafy.yeogirok.global.response;

import java.time.ZoneId;

/** 응답 datetime 형식 {@code "YYYY-MM-DD HH:mm:ss+09"}. {@code Instant} 필드의 @JsonFormat 에 쓴다. */
public final class ApiDateTime {

	/** {@code X} 는 분이 0 이면 시간만 쓴다(+09). */
	public static final String PATTERN = "yyyy-MM-dd HH:mm:ssX";
	public static final String TIME_ZONE = "Asia/Seoul";
	/** 서비스 기준 시간대. 날짜 조건·04:00 구간 계산에 쓴다. */
	public static final ZoneId ZONE = ZoneId.of(TIME_ZONE);

	private ApiDateTime() {
	}
}
