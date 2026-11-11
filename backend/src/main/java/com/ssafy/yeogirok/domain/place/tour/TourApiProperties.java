package com.ssafy.yeogirok.domain.place.tour;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 한국관광공사 국문 관광정보(TourAPI KorService2) 설정. 인증키는 환경 변수 {@code TOUR_API_KEY} 로만 받는다.
 * 공공데이터포털의 일반 인증키 중 Decoding 값을 넣는다(호출할 때 한 번 인코딩한다). 비어 있으면 호출하지 않는다.
 *
 * @param mobileApp 필수 파라미터 MobileApp(서비스 이름)
 */
@Validated
@ConfigurationProperties("yeogirok.tour-api")
public record TourApiProperties(
	String serviceKey,
	@NotBlank String baseUrl,
	@NotBlank String mobileApp,
	@NotNull Duration connectTimeout,
	@NotNull Duration readTimeout
) {

	public boolean hasServiceKey() {
		return serviceKey != null && !serviceKey.isBlank();
	}

	/** 실수로 로그에 찍혀도 키가 나가지 않게 한다. */
	@Override
	public String toString() {
		return "TourApiProperties[serviceKey=%s, baseUrl=%s, mobileApp=%s, connectTimeout=%s, readTimeout=%s]"
			.formatted(hasServiceKey() ? "****" : "(없음)", baseUrl, mobileApp, connectTimeout, readTimeout);
	}
}
