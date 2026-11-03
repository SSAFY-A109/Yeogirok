package com.ssafy.yeogirok.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** API-006·007 요청. */
public record RefreshTokenRequest(
	@NotBlank(message = "refreshToken 은 필수입니다.")
	String refreshToken
) {
}
