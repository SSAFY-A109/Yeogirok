package com.ssafy.yeogirok.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** API-009 요청. 본문은 구글 회원만 보낸다. */
public record WithdrawRequest(
	@Schema(description = "구글 회원만 필수. 탈퇴 직전 재로그인 때 구글 SDK 가 준 serverAuthCode. 서버가 구글 연결 해제에 쓴다",
		nullable = true)
	String googleAuthCode
) {
}
