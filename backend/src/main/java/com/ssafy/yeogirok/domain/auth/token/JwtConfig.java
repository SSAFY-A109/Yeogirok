package com.ssafy.yeogirok.domain.auth.token;

import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import com.ssafy.yeogirok.domain.user.domain.UserRole;

/**
 * 서비스 액세스 토큰(HS256 JWT) 발급·검증. 이 앱의 {@code JwtDecoder} 빈은 이것 하나뿐이다.
 */
@Configuration
public class JwtConfig {

	static final String ROLE_CLAIM = "role";

	private static final Set<String> ALLOWED_ROLES = Arrays.stream(UserRole.values())
		.map(Enum::name)
		.collect(Collectors.toUnmodifiableSet());

	@Bean
	public SecretKey serviceJwtSecretKey(JwtProperties properties) {
		byte[] secretBytes;
		try {
			secretBytes = Base64.getDecoder().decode(properties.secret());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("JWT_SECRET 은 Base64 형식이어야 합니다.", e);
		}

		if (secretBytes.length < 32) {
			throw new IllegalArgumentException("JWT_SECRET 은 디코드했을 때 32바이트 이상이어야 합니다.");
		}
		return new SecretKeySpec(secretBytes, "HmacSHA256");
	}

	@Bean
	public JwtEncoder serviceJwtEncoder(SecretKey serviceJwtSecretKey) {
		return NimbusJwtEncoder.withSecretKey(serviceJwtSecretKey).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	public JwtDecoder serviceJwtDecoder(SecretKey serviceJwtSecretKey, JwtProperties properties, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(serviceJwtSecretKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();

		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
			new AccessTokenExpiryValidator(clock),
			new JwtIssuerValidator(properties.issuer()),
			new JwtClaimValidator<String>(JwtClaimNames.SUB, StringUtils::hasText),
			new JwtClaimValidator<String>(ROLE_CLAIM, ALLOWED_ROLES::contains)));
		return decoder;
	}
}
