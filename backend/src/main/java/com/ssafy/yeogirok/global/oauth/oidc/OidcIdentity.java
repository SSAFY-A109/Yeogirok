package com.ssafy.yeogirok.global.oauth.oidc;

import com.ssafy.yeogirok.global.oauth.OAuthProvider;

/**
 * 검증을 마친 ID 토큰에서 꺼낸 사용자 정보. {@code subject} 외에는 동의·scope 에 따라 null 일 수 있다.
 */
public record OidcIdentity(
	OAuthProvider provider,
	String subject,
	String email,
	String nickname,
	String profileImageUrl
) {
}
