package com.ssafy.yeogirok.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** API-005 요청. */
public record TokenIssueRequest(
	@Schema(description = "소셜 제공자", allowableValues = {"google", "kakao"}, example = "google")
	@NotBlank(message = "provider 는 필수입니다.")
	String provider,

	@Schema(description = "제공자 SDK 가 준 OIDC ID 토큰 원문")
	@NotBlank(message = "idToken 은 필수입니다.")
	String idToken,

	@Schema(description = "앱이 로그인 직전 만들어 SDK 에 넘긴 값. ID 토큰의 nonce 클레임과 같아야 한다")
	@NotBlank(message = "nonce 는 필수입니다.")
	String nonce
) {
}
