package com.ssafy.yeogirok.support;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;

import com.ssafy.yeogirok.global.oauth.OAuthProvider;
import com.ssafy.yeogirok.global.oauth.oidc.IdTokenValidators;
import com.ssafy.yeogirok.global.oauth.oidc.IdTokenVerifier;
import com.ssafy.yeogirok.global.oauth.oidc.OidcProperties;

/**
 * 실제 JWKS 대신 테스트 공개키로 검증하는 IdTokenVerifier, 앞으로 돌릴 수 있는 시계,
 * 리프레시 토큰을 둘 실제 Redis(Testcontainers, infra 로컬 compose 와 같은 이미지).
 */
@TestConfiguration
public class TestAuthConfig {

	public static final Instant START = Instant.parse("2026-10-28T00:00:00Z");

	/** 테스트 JVM 하나에서 한 번만 띄운다. 끝나면 Testcontainers 가 지운다. */
	private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

	@Bean
	public DynamicPropertyRegistrar redisProperties() {
		REDIS.start();
		return registry -> {
			registry.add("spring.data.redis.host", REDIS::getHost);
			registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		};
	}

	@Bean
	public TestIdTokens testIdTokens() {
		return new TestIdTokens();
	}

	@Bean
	@Primary
	public MutableClock mutableClock() {
		return new MutableClock(START);
	}

	@Bean
	@Primary
	public IdTokenVerifier testIdTokenVerifier(TestIdTokens tokens, OidcProperties properties, MutableClock clock) {
		Map<OAuthProvider, JwtDecoder> decoders = new EnumMap<>(OAuthProvider.class);
		decoders.put(OAuthProvider.GOOGLE, decoder(tokens, OAuthProvider.GOOGLE, properties.google(), clock));
		decoders.put(OAuthProvider.KAKAO, decoder(tokens, OAuthProvider.KAKAO, properties.kakao(), clock));
		return new IdTokenVerifier(decoders);
	}

	private JwtDecoder decoder(TestIdTokens tokens, OAuthProvider provider, OidcProperties.Provider config,
		MutableClock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(tokens.publicKey()).build();
		decoder.setJwtValidator(IdTokenValidators.create(provider, config.allowedAudiences(), clock));
		return decoder;
	}
}
