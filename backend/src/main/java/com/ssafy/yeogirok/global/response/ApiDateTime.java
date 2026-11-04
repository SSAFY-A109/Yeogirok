package com.ssafy.yeogirok.global.response;

/**
 * 응답 datetime 형식 {@code "YYYY-MM-DD HH:mm:ss+09"}. {@code Instant} 필드에 붙인다.
 *
 * <pre>{@code
 * @JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
 * Instant createdAt
 * }</pre>
 */
public final class ApiDateTime {

	/** {@code X} 는 분이 0 이면 시간만 쓴다(+09). */
	public static final String PATTERN = "yyyy-MM-dd HH:mm:ssX";
	public static final String TIME_ZONE = "Asia/Seoul";

	private ApiDateTime() {
	}
}
