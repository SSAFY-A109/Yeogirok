package com.ssafy.yeogirok.global.location;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 현재 위치 증거 기준(DEC-01). 일부러 기본값이 없어 값이 없으면 앱이 시작하지 않는다. 기준을 바꾸면 policyVersion 도 바꾼다.
 *
 * @param maxAge         capturedAt 이 서버 시각보다 이만큼 넘게 과거면 C009
 * @param maxFutureSkew  이만큼 넘게 미래면 C009(기기 시계 오차 허용)
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
