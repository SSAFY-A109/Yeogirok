package com.ssafy.yeogirok.global.geo;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 카카오 로컬 API 설정. 키는 환경 변수 {@code KAKAO_REST_API_KEY} 로만 받고, 비어 있으면 호출하지 않는다(주소 등 null).
 *
 * @param readTimeout 응답 제한 시간. 멱등키 처리 중 표시 수명보다 충분히 짧아야 한다
 */
@Validated
@ConfigurationProperties("yeogirok.kakao.local")
public record KakaoLocalProperties(
	String restApiKey,
	@NotBlank String baseUrl,
	@NotNull Duration connectTimeout,
	@NotNull Duration readTimeout
) {

	public boolean hasRestApiKey() {
		return restApiKey != null && !restApiKey.isBlank();
	}

	/** 실수로 로그에 찍혀도 키가 나가지 않게 한다. */
	@Override
	public String toString() {
		return "KakaoLocalProperties[restApiKey=%s, baseUrl=%s, connectTimeout=%s, readTimeout=%s]"
			.formatted(hasRestApiKey() ? "****" : "(없음)", baseUrl, connectTimeout, readTimeout);
	}
}
