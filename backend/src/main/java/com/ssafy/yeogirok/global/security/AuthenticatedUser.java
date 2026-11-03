package com.ssafy.yeogirok.global.security;

import java.security.Principal;

/** 인증된 요청의 principal. 컨트롤러에서 {@code @AuthenticationPrincipal AuthenticatedUser user} 로 받는다. */
public record AuthenticatedUser(
	Long userId
) implements Principal {

	@Override
	public String getName() {
		return userId.toString();
	}
}
