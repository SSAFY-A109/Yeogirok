package com.ssafy.yeogirok.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.user.dto.MyAccountResponse;
import com.ssafy.yeogirok.domain.user.dto.WithdrawRequest;
import com.ssafy.yeogirok.domain.user.dto.WithdrawResponse;
import com.ssafy.yeogirok.domain.user.service.UserService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "USER")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@Operation(summary = "API-008 내 계정 정보 조회", security = @SecurityRequirement(name = "bearerAuth"))
	@GetMapping("/me")
	public ResponseEntity<ApiResponseBody<MyAccountResponse>> getMyAccount(
		@AuthenticationPrincipal AuthenticatedUser user) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(userService.getMyAccount(user.userId())));
	}

	@Operation(summary = "API-009 회원 탈퇴",
		description = "소셜 로그인(API-005) 직후 받은 액세스 토큰으로만 탈퇴할 수 있다(아니면 A011). 구글 회원은 본문에 googleAuthCode 가 필요하다.",
		security = @SecurityRequirement(name = "bearerAuth"))
	@DeleteMapping("/me")
	public ResponseEntity<ApiResponseBody<WithdrawResponse>> withdraw(@AuthenticationPrincipal AuthenticatedUser user,
		@RequestBody(required = false) WithdrawRequest request) {
		String googleAuthCode = request == null ? null : request.googleAuthCode();
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(userService.withdraw(user, googleAuthCode)));
	}
}
