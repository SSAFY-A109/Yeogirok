package com.ssafy.yeogirok.domain.auth.token;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 탈퇴 회원의 남은 액세스 토큰을 막는다. 액세스 토큰은 서명만 보므로 탈퇴 뒤에도 만료(최대 TTL)까지 쓸 수 있다.
 *
 * <p>탈퇴할 때 {@code auth:withdrawn:{userId}} 를 액세스 토큰 수명만큼 남기고, JWT 검증 단계에서 이 키가 있으면
 * 무효(A003)로 본다. 그래서 회원을 조회하지 않는 API 도 도메인마다 따로 탈퇴 여부를 확인하지 않아도 된다.
 *
 * <p>Redis 에 연결할 수 없으면 막지 않고 통과시킨다(로그). 탈퇴 직후 토큰 수명 안의 요청만 해당되므로,
 * Redis 장애로 모든 API 가 멈추는 것보다 낫다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnUserTokens implements OAuth2TokenValidator<Jwt> {

	private static final String WITHDRAWN_KEY = "auth:withdrawn:";
	/** 액세스 토큰 만료 검사의 시계 오차 허용과 같다({@link AccessTokenExpiryValidator}). */
	private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

	private final StringRedisTemplate redis;
	private final JwtProperties properties;

	/** 탈퇴가 커밋된 뒤 부른다. 그 회원에게 이미 발급된 액세스 토큰이 모두 만료될 때까지 남긴다. */
	public void markWithdrawn(Long userId) {
		redis.opsForValue().set(WITHDRAWN_KEY + userId, "1", properties.accessTokenTtl().plus(CLOCK_SKEW));
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		try {
			if (Boolean.TRUE.equals(redis.hasKey(WITHDRAWN_KEY + jwt.getSubject()))) {
				return OAuth2TokenValidatorResult.failure(
					new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "user withdrawn", null));
			}
		} catch (RuntimeException e) {
			log.warn("Could not check withdrawn users. Letting the token through. reason={}", e.getMessage());
		}
		return OAuth2TokenValidatorResult.success();
	}
}
