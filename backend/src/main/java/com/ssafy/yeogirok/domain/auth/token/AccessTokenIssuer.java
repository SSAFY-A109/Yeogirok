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

	public String issue(User user) {
		Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);

		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(properties.issuer())
			.subject(user.getId().toString())
			.issuedAt(issuedAt)
			.expiresAt(issuedAt.plus(properties.accessTokenTtl()))
			.id(UUID.randomUUID().toString())
			.claim(JwtConfig.ROLE_CLAIM, user.getRole().name())
			.build();

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return serviceJwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public long ttlSeconds() {
		return properties.accessTokenTtl().toSeconds();
	}
}
