package com.ssafy.yeogirok.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.ssafy.yeogirok.global.oauth.OAuthProvider;

/**
 * 구글·카카오 대신 테스트 RSA 키로 ID 토큰을 만든다. 검증 규칙(iss·aud·exp·sub)은 운영과 같은 것을 쓴다.
 */
public class TestIdTokens {

	public static final String GOOGLE_AUDIENCE = "test-google-web-client-id";
	public static final String KAKAO_AUDIENCE = "test-kakao-app-key";
	public static final String NONCE = "test-nonce";

	private final KeyPair keyPair;
	private final NimbusJwtEncoder encoder;

	public TestIdTokens() {
		this.keyPair = generateKeyPair();
		this.encoder = NimbusJwtEncoder.withKeyPair((RSAPublicKey) keyPair.getPublic(),
			(RSAPrivateKey) keyPair.getPrivate()).build();
	}

	public RSAPublicKey publicKey() {
		return (RSAPublicKey) keyPair.getPublic();
	}

	/** 앱이 {@link #NONCE} 를 SDK 에 넘겨 받은 토큰. */
	public String google(String subject, Instant now) {
		return create(OAuthProvider.GOOGLE, subject, now, claims -> claims.claim("nonce", NONCE));
	}

	/** 앱이 {@link #NONCE} 를 SDK 에 넘겨 받은 토큰. */
	public String kakao(String subject, Instant now) {
		return create(OAuthProvider.KAKAO, subject, now, claims -> claims.claim("nonce", NONCE));
	}

	public String create(OAuthProvider provider, String subject, Instant now, Consumer<JwtClaimsSet.Builder> customizer) {
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer(provider.getIssuers().iterator().next())
			.subject(subject)
			.audience(List.of(provider == OAuthProvider.GOOGLE ? GOOGLE_AUDIENCE : KAKAO_AUDIENCE))
			.issuedAt(now)
			.expiresAt(now.plus(Duration.ofHours(1)))
			.claims(map -> map.putAll(Map.of("email", subject + "@example.com", "picture", "https://example.com/p.png")));
		claims.claim(provider == OAuthProvider.GOOGLE ? "name" : "nickname", "닉네임-" + subject);
		customizer.accept(claims);

		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	private static KeyPair generateKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
