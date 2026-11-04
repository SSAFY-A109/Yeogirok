package com.ssafy.yeogirok.global.oauth.oidc;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.util.StringUtils;

import com.ssafy.yeogirok.global.oauth.OAuthProvider;

/**
 * 소셜 ID 토큰 검증 규칙(서명 외). 운영 디코더와 테스트 디코더가 같은 규칙을 쓰도록 한곳에 둔다.
 *
 * <p>nonce 는 요청마다 값이 달라 디코더 단계가 아니라 {@link IdTokenVerifier} 에서 본다.
 */
public final class IdTokenValidators {

	private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

	private IdTokenValidators() {
	}

	public static OAuth2TokenValidator<Jwt> create(OAuthProvider provider, List<String> allowedAudiences, Clock clock) {
		JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
		timestampValidator.setClock(clock);

		return new DelegatingOAuth2TokenValidator<>(
			timestampValidator,
			issuerValidator(provider),
			audienceValidator(allowedAudiences),
			new JwtClaimValidator<String>(JwtClaimNames.SUB, StringUtils::hasText));
	}

	/** iss 는 URL 로 바뀌지 않은 원문 문자열로 비교한다. 구글의 스킴 없는 "accounts.google.com" 도 받아야 해서다. */
	private static OAuth2TokenValidator<Jwt> issuerValidator(OAuthProvider provider) {
		return jwt -> {
			Object issuer = jwt.getClaims().get(JwtClaimNames.ISS);
			if (issuer != null && provider.getIssuers().contains(issuer.toString())) {
				return OAuth2TokenValidatorResult.success();
			}
			return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "issuer 불일치: " + issuer, null));
		};
	}

	private static OAuth2TokenValidator<Jwt> audienceValidator(List<String> allowedAudiences) {
		return jwt -> {
			List<String> audiences = jwt.getAudience();
			if (audiences != null && audiences.stream().anyMatch(allowedAudiences::contains)) {
				return OAuth2TokenValidatorResult.success();
			}
			return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience 불일치: " + audiences, null));
		};
	}
}
