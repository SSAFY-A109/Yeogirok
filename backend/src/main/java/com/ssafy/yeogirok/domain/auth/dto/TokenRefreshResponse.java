package com.ssafy.yeogirok.domain.auth.dto;

/** API-006 응답 data. API-005 에서 isNewUser 만 뺀 형태. */
public record TokenRefreshResponse(
	String accessToken,
	long accessTokenExpiresIn,
	String refreshToken,
	long refreshTokenExpiresIn
) {
}
