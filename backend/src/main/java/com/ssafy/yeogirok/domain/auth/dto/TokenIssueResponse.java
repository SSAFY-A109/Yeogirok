package com.ssafy.yeogirok.domain.auth.dto;

/** API-005 응답 data. */
public record TokenIssueResponse(
	String accessToken,
	long accessTokenExpiresIn,
	String refreshToken,
	long refreshTokenExpiresIn,
	boolean isNewUser
) {
}
