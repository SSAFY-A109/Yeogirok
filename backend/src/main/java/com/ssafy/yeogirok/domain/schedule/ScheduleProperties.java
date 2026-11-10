package com.ssafy.yeogirok.domain.schedule;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 계획 일정 설정. 기본값을 두지 않는다. 값이 없거나 0 이하면 앱이 시작할 때 실패한다.
 *
 * @param draftRetention 일정 초안을 보관하는 기간. 만든 시각부터 센다(다시 계산해도 늘지 않는다)
 */
@ConfigurationProperties("yeogirok.schedule")
public record ScheduleProperties(Duration draftRetention) {

	public ScheduleProperties {
		if (draftRetention == null || draftRetention.isZero() || draftRetention.isNegative()) {
			throw new IllegalArgumentException("yeogirok.schedule.draft-retention 값이 필요합니다(0보다 커야 합니다).");
		}
	}
}
