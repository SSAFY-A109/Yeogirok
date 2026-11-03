package com.ssafy.yeogirok.global.idempotency;

import java.util.Objects;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 멱등키 하나. Redis 키는 {@code request:{userId}:{scope}:{target}:{requestKey}} 다.
 *
 * <p>같은 {@code requestKey} 라도 사용자·요청 종류·대상이 다르면 서로 다른 키다.
 *
 * @param userId     요청한 사용자
 * @param scope      요청 종류. API 마다 하나씩 정한다(예: {@code footprint-create}, {@code tip-publish})
 * @param target     요청 대상 자원 ID. 새로 만드는 요청처럼 대상이 없으면 {@code "-"} 를 쓴다
 * @param requestKey 앱이 보낸 {@code Idempotency-Key} 헤더 값
 */
public record IdempotencyKey(Long userId, String scope, String target, String requestKey) {

	private static final String PREFIX = "request";

	public IdempotencyKey {
		Objects.requireNonNull(userId, "userId");
		requireSegment(scope, "scope");
		requireSegment(target, "target");
		// 헤더가 빈 값으로 온 경우. 헤더 자체가 없으면 컨트롤러의 @RequestHeader 가 먼저 C001 로 거른다.
		if (requestKey == null || requestKey.isBlank()) {
			throw new BusinessException(ExceptionType.REQUIRED_VALUE_MISSING, "Idempotency-Key 헤더가 필요합니다.");
		}
	}

	/** scope·target 은 서버 코드가 정하는 값이다. 비었거나 구분자(:)가 들어 있으면 코드 실수다. */
	private static void requireSegment(String value, String name) {
		if (value == null || value.isBlank() || value.contains(":")) {
			throw new IllegalArgumentException("%s 는 비어 있지 않고 ':' 이 없어야 합니다: %s".formatted(name, value));
		}
	}

	public String toRedisKey() {
		return String.join(":", PREFIX, userId.toString(), scope, target, requestKey);
	}
}
