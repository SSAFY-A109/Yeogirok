package com.ssafy.yeogirok.global.location;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 현재 위치 증거 기준(노션 DEC-01). 수치가 아직 정해지지 않아 <b>기본값이 없다.</b> 값이 없으면 앱이 시작하지 않는다.
 *
 * <p>기준을 바꾸면 {@code policyVersion} 도 바꾼다. 저장한 기록의 {@code location_policy_version} 으로
 * 어떤 기준으로 확인했는지 남는다.
 *
 * @param maxAge         capturedAt 이 서버 시각보다 이만큼 넘게 과거면 만료(C009)
 * @param maxFutureSkew  capturedAt 이 서버 시각보다 이만큼 넘게 미래면 만료(C009). 기기 시계 오차 허용 범위
 * @param policyVersion  위치 증거 기준 버전. footprints.location_policy_version 에 저장한다
 */
@Validated
@ConfigurationProperties("yeogirok.location-evidence")
public record LocationEvidenceProperties(
	@NotNull Duration maxAge,
	@NotNull Duration maxFutureSkew,
	@NotBlank String policyVersion
) {

	public LocationEvidenceProperties {
		requireNotNegative(maxAge, "max-age");
		requireNotNegative(maxFutureSkew, "max-future-skew");
	}

	private static void requireNotNegative(Duration value, String name) {
		if (value != null && value.isNegative()) {
			throw new IllegalArgumentException("yeogirok.location-evidence." + name + " 는 0 이상이어야 합니다: " + value);
		}
	}
}
