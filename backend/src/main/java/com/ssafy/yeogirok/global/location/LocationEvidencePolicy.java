package com.ssafy.yeogirok.global.location;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.Coordinates;

import lombok.RequiredArgsConstructor;

/** 현재 위치 증거 검사(API-041·047·048·049·052 공통). 좌표 범위 C011, 측정 시각 유효 범위 C009. */
@Component
@RequiredArgsConstructor
public class LocationEvidencePolicy {

	private final LocationEvidenceProperties properties;

	/** @throws BusinessException 위도 -90~90, 경도 -180~180 밖이면 C011({@link Coordinates#checkRange}) */
	public void checkCoordinates(BigDecimal latitude, BigDecimal longitude) {
		Coordinates.checkRange(latitude, longitude);
	}

	/** @throws BusinessException capturedAt 이 {@code now} 기준 허용 범위(과거 maxAge, 미래 maxFutureSkew) 밖이면 C009 */
	public void checkFresh(Instant capturedAt, Instant now) {
		if (capturedAt.isBefore(now.minus(properties.maxAge()))
			|| capturedAt.isAfter(now.plus(properties.maxFutureSkew()))) {
			throw new BusinessException(ExceptionType.LOCATION_EVIDENCE_EXPIRED);
		}
	}

	public String policyVersion() {
		return properties.policyVersion();
	}
}
