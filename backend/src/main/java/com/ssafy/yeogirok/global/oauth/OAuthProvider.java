package com.ssafy.yeogirok.global.oauth;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import lombok.Getter;

/**
 * 소셜 로그인 제공자와 OIDC 검증 기준값.
 *
 * <p>issuer·JWKS 는 각 제공자의 discovery 문서(/.well-known/openid-configuration)에서 확인한 값이다(2026-10-28).
 * 구글은 iss 를 스킴 없는 형태로 줄 때가 있어 둘 다 허용한다.
 */
@Getter
public enum OAuthProvider {

	GOOGLE("google", Set.of("https://accounts.google.com", "accounts.google.com"),
		"https://www.googleapis.com/oauth2/v3/certs"),

	KAKAO("kakao", Set.of("https://kauth.kakao.com"),
		"https://kauth.kakao.com/.well-known/jwks.json");

	/** API 요청·경로에서 쓰는 소문자 이름. */
	private final String value;
	private final Set<String> issuers;
	private final String jwkSetUri;

	OAuthProvider(String value, Set<String> issuers, String jwkSetUri) {
		this.value = value;
		this.issuers = issuers;
		this.jwkSetUri = jwkSetUri;
	}

	public static Optional<OAuthProvider> fromValue(String value) {
		return Arrays.stream(values()).filter(provider -> provider.value.equals(value)).findFirst();
	}
}
