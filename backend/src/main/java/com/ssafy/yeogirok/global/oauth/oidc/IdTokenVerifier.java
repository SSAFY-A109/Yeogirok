package com.ssafy.yeogirok.global.oauth.oidc;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.StringUtils;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * 소셜 ID 토큰을 검증한다.
 *
 * <p>디코더는 빈으로 노출하지 않는다. {@code JwtDecoder} 빈이 여럿이면 서비스 액세스 토큰 검증용 디코더와 섞인다.
 */
@Slf4j
public class IdTokenVerifier {

	private final Map<OAuthProvider, JwtDecoder> decoders;

	public IdTokenVerifier(Map<OAuthProvider, JwtDecoder> decoders) {
		this.decoders = new EnumMap<>(decoders);
	}

	/**
	 * 앱이 SDK 로 받아 보낸 ID 토큰(API-005). 탈취한 토큰의 재사용을 막으려고 nonce 가 반드시 같아야 한다.
	 *
	 * @param nonce 앱이 로그인 직전 만들어 SDK 에 넘긴 값. 토큰에 nonce 가 없어도 불일치로 본다.
	 */
	public OidcIdentity verify(OAuthProvider provider, String idToken, String nonce) {
		Jwt jwt = decode(provider, idToken);

		if (!StringUtils.hasText(nonce) || !nonce.equals(jwt.getClaimAsString("nonce"))) {
			throw new BusinessException(ExceptionType.ID_TOKEN_NONCE_MISMATCH);
		}

		return toIdentity(provider, jwt);
	}

	/**
	 * 서버가 제공자 토큰 엔드포인트에서 직접 받은 ID 토큰(구글 인증 코드 교환). 앱을 거치지 않았으므로 nonce 를 보지 않는다.
	 */
	public OidcIdentity verifyFromTokenEndpoint(OAuthProvider provider, String idToken) {
		return toIdentity(provider, decode(provider, idToken));
	}

	private OidcIdentity toIdentity(OAuthProvider provider, Jwt jwt) {
		return new OidcIdentity(provider, jwt.getSubject(), jwt.getClaimAsString("email"), nickname(provider, jwt),
			jwt.getClaimAsString("picture"));
	}

	private Jwt decode(OAuthProvider provider, String idToken) {
		JwtDecoder decoder = decoders.get(provider);
		if (decoder == null) {
			throw new IllegalStateException("ID 토큰 디코더가 없습니다: " + provider);
		}

		try {
			return decoder.decode(idToken);
		} catch (JwtException e) {
			// 원인(서명·만료·aud 등)은 로그에만 남긴다. 응답으로 검증 단서를 주지 않는다.
			log.info("ID token rejected. provider={}, reason={}", provider, e.getMessage());
			throw new BusinessException(ExceptionType.ID_TOKEN_INVALID);
		}
	}

	/** 구글은 profile scope 의 {@code name}, 카카오는 OIDC 의 {@code nickname} 클레임. */
	private String nickname(OAuthProvider provider, Jwt jwt) {
		return switch (provider) {
			case GOOGLE -> jwt.getClaimAsString("name");
			case KAKAO -> jwt.getClaimAsString("nickname");
		};
	}
}
