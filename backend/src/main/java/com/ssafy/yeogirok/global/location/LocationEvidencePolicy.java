package com.ssafy.yeogirok.global.location;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 현재 위치 증거(좌표 + 측정 시각) 검사. 현장 위치를 받는 API(041·047·048·049·052)가 같이 쓴다.
 *
 * <p>값이 비었는지(C008)·형식(C002)은 요청을 읽을 때 본다. 여기서는 좌표 범위(C011)와 측정 시각의 유효 범위(C009)를 본다.
 */
@Component
@RequiredArgsConstructor
public class LocationEvidencePolicy {

	private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
	private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

	private final LocationEvidenceProperties properties;

	/** @throws BusinessException 위도 -90~90, 경도 -180~180 밖이면 C011 */
	public void checkCoordinates(BigDecimal latitude, BigDecimal longitude) {
		if (latitude.abs().compareTo(MAX_LATITUDE) > 0 || longitude.abs().compareTo(MAX_LONGITUDE) > 0) {
			throw new BusinessException(ExceptionType.COORDINATE_OUT_OF_RANGE);
		}
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
