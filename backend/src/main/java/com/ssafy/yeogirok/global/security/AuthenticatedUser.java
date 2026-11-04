package com.ssafy.yeogirok.global.security;

import java.security.Principal;
import java.time.Instant;

/**
 * 인증된 요청의 principal. 컨트롤러에서 {@code @AuthenticationPrincipal AuthenticatedUser user} 로 받는다.
 *
 * @param authenticatedAt 소셜 로그인(API-005) 시각. 재발급(API-006) 토큰이면 null 이다.
 */
public record AuthenticatedUser(
	Long userId,
	Instant authenticatedAt
) implements Principal {

	@Override
	public String getName() {
		return userId.toString();
	}
}
