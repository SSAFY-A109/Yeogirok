package com.ssafy.yeogirok.global.idempotency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 멱등키 설정값. 정하지 않은 수치라 기본값을 두지 않는다. 값이 없거나 0 이하면 앱이 시작할 때 실패한다.
 *
 * @param retention         처리가 끝난 멱등키를 보관하는 기간. 요청 종류와 상관없이 같다(11/3 결정 24시간).
 * @param processingTimeout '처리 중' 표시의 수명. 서버가 처리 도중 죽어도 이 시간이 지나면 표시가 풀린다(30초 제안).
 */
@ConfigurationProperties("yeogirok.idempotency")
public record IdempotencyProperties(Duration retention, Duration processingTimeout) {

	public IdempotencyProperties {
		requirePositive(retention, "yeogirok.idempotency.retention");
		requirePositive(processingTimeout, "yeogirok.idempotency.processing-timeout");
	}

	private static void requirePositive(Duration value, String name) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException("%s 값이 필요합니다(0보다 커야 합니다).".formatted(name));
		}
	}
}
