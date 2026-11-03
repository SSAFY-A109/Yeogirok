package com.ssafy.yeogirok.domain.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.auth.dto.RefreshTokenRequest;
import com.ssafy.yeogirok.domain.auth.dto.TokenIssueRequest;
import com.ssafy.yeogirok.domain.auth.dto.TokenIssueResponse;
import com.ssafy.yeogirok.domain.auth.dto.TokenRefreshResponse;
import com.ssafy.yeogirok.domain.auth.service.AuthService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "AUTH")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@Operation(summary = "API-005 소셜 ID 토큰으로 서비스 토큰 발급")
	@PostMapping("/token")
	public ResponseEntity<ApiResponseBody<TokenIssueResponse>> issueToken(
		@Valid @RequestBody TokenIssueRequest request) {
		TokenIssueResponse data = authService.login(request.provider(), request.idToken(), request.nonce());
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(data));
	}

	@Operation(summary = "API-006 서비스 토큰 재발급")
	@PostMapping("/token/refresh")
	public ResponseEntity<ApiResponseBody<TokenRefreshResponse>> refreshToken(
		@Valid @RequestBody RefreshTokenRequest request) {
		TokenRefreshResponse data = authService.refresh(request.refreshToken());
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(data));
	}

	@Operation(summary = "API-007 로그아웃", security = @SecurityRequirement(name = "bearerAuth"))
	@PostMapping("/logout")
	public ResponseEntity<ApiResponseBody<Void>> logout(@AuthenticationPrincipal AuthenticatedUser user,
		@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(user.userId(), request.refreshToken());
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse());
	}
}
