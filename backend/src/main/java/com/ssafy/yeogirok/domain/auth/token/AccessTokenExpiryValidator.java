package com.ssafy.yeogirok.domain.auth.token;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * 만료(A002)와 그 밖의 무효(A003)를 앱이 다르게 처리하므로, 만료일 때 <b>이 클래스가 정한 오류 코드</b>를 남긴다.
 *
 * <p>스프링의 만료 오류는 설명 문자열("Jwt expired at …")로만 구분돼서, 그 문구에 기대면 라이브러리
 * 업데이트에 조용히 깨진다. 판정은 {@link JwtTimestampValidator} 에 맡기고 결과만 바꿔 단다.
 */
public class AccessTokenExpiryValidator implements OAuth2TokenValidator<Jwt> {

	public static final String EXPIRED_ERROR_CODE = "yeogirok_access_token_expired";

	private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

	private final JwtTimestampValidator delegate = new JwtTimestampValidator(CLOCK_SKEW);
	private final Clock clock;

	public AccessTokenExpiryValidator(Clock clock) {
		this.clock = clock;
		this.delegate.setClock(clock);
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		OAuth2TokenValidatorResult result = delegate.validate(jwt);
		if (!result.hasErrors()) {
			return result;
		}

		Instant expiresAt = jwt.getExpiresAt();
		boolean expired = expiresAt != null && clock.instant().minus(CLOCK_SKEW).isAfter(expiresAt);
		return expired
			? OAuth2TokenValidatorResult.failure(new OAuth2Error(EXPIRED_ERROR_CODE, "access token expired", null))
			: result;
	}
}
