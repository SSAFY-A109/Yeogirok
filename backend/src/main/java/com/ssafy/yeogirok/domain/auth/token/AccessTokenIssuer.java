package com.ssafy.yeogirok.domain.auth.token;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.user.domain.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccessTokenIssuer {

	private final JwtEncoder serviceJwtEncoder;
	private final JwtProperties properties;
	private final Clock clock;

	/** API-005. 소셜 로그인 시각을 {@code auth_time} 으로 담는다. */
	public String issueForLogin(User user) {
		return issue(user, true);
	}

	/** API-006. 재발급 토큰은 {@code auth_time} 이 없어 최근 재인증으로 보지 않는다. */
	public String issueForRefresh(User user) {
		return issue(user, false);
	}

	private String issue(User user, boolean afterLogin) {
		Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);

		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(user.getId().toString())
			.issuedAt(issuedAt)
			.expiresAt(issuedAt.plus(properties.accessTokenTtl()))
			.id(UUID.randomUUID().toString())
			.claim(JwtConfig.ROLE_CLAIM, user.getRole().name());
		if (afterLogin) {
			// 표준 클레임(iat·exp)과 같이 epoch 초로 넣는다. Instant 를 그대로 넣으면 JSON 으로 직렬화되지 않는다.
			claims.claim(JwtConfig.AUTH_TIME_CLAIM, issuedAt.getEpochSecond());
		}

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return serviceJwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	public long ttlSeconds() {
		return properties.accessTokenTtl().toSeconds();
	}
}
