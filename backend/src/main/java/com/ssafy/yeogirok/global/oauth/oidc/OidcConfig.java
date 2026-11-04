package com.ssafy.yeogirok.global.oauth.oidc;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.ssafy.yeogirok.global.oauth.OAuthProvider;

@Configuration
public class OidcConfig {

	@Bean
	public IdTokenVerifier idTokenVerifier(OidcProperties properties, Clock clock) {
		return new IdTokenVerifier(Map.of(
			OAuthProvider.GOOGLE, decoder(OAuthProvider.GOOGLE, properties.google().allowedAudiences(), clock),
			OAuthProvider.KAKAO, decoder(OAuthProvider.KAKAO, properties.kakao().allowedAudiences(), clock)));
	}

	/** 공개키는 JWKS 에서 받아 캐시한다(Nimbus 기본 동작). 키가 바뀌면 kid 불일치로 다시 받는다. */
	private JwtDecoder decoder(OAuthProvider provider, List<String> allowedAudiences, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(provider.getJwkSetUri()).build();
		decoder.setJwtValidator(IdTokenValidators.create(provider, allowedAudiences, clock));
		return decoder;
	}
}
