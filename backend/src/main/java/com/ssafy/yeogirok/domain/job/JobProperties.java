package com.ssafy.yeogirok.domain.job;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 비동기 작업 설정. 기본값을 두지 않는다. 값이 없거나 0 이하면 앱이 시작할 때 실패한다.
 *
 * @param retention  작업 기록을 보관하는 기간. 만든 시각부터 센다
 * @param runTimeout 이 시간 안에 끝나지 않은 작업은 실패(다시 시도 가능)로 본다
 * @param threads    작업을 실행하는 스레드 수
 */
@ConfigurationProperties("yeogirok.job")
public record JobProperties(Duration retention, Duration runTimeout, int threads) {

	public JobProperties {
		requirePositive(retention, "yeogirok.job.retention");
		requirePositive(runTimeout, "yeogirok.job.run-timeout");
		if (threads < 1) {
			throw new IllegalArgumentException("yeogirok.job.threads 값이 필요합니다(1 이상).");
		}
		if (runTimeout.compareTo(retention) >= 0) {
			throw new IllegalArgumentException("yeogirok.job.run-timeout 은 retention 보다 짧아야 합니다.");
		}
	}

	private static void requirePositive(Duration value, String name) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException("%s 값이 필요합니다(0보다 커야 합니다).".formatted(name));
		}
	}
}
