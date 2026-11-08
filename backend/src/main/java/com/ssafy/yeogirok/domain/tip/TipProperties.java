package com.ssafy.yeogirok.domain.tip;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * AR 팁 거리·개수·배치 크기. 정해진 값이라 공통 설정(application.yaml)에 둔다(specs/ar-tip-api.md 5절).
 *
 * @param searchRadiusMeters      주변 탐색 반경(m, 경계 포함)
 * @param interactionRadiusMeters 상세·반응 허용 거리(m, 경계 포함). 넘으면 T004
 * @param maxSearchResults        주변 탐색 최대 개수
 * @param placementMaxBytes       placement JSON(UTF-8) 최대 바이트. 넘으면 T007
 */
@Validated
@ConfigurationProperties("yeogirok.tip")
public record TipProperties(
	@NotNull @Positive Integer searchRadiusMeters,
	@NotNull @Positive Integer interactionRadiusMeters,
	@NotNull @Positive Integer maxSearchResults,
	@NotNull @Positive Integer placementMaxBytes
) {

	public boolean canInteract(double distanceMeters) {
		return distanceMeters <= interactionRadiusMeters;
	}

	public boolean inSearchRadius(double distanceMeters) {
		return distanceMeters <= searchRadiusMeters;
	}
}
