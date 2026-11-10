package com.ssafy.yeogirok.domain.push.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.push.dto.PushTokenDeleteRequest;
import com.ssafy.yeogirok.domain.push.dto.PushTokenRegisterRequest;
import com.ssafy.yeogirok.domain.push.service.PushTokenService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Android 채팅 푸시 기기 토큰(API-095·096). 발송 규칙은 노션 API-077 "채팅 푸시 알림 (Android)". */
@Tag(name = "PUSH")
@RestController
@RequestMapping("/api/v1/users/me/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

	private final PushTokenService pushTokenService;

	@Operation(summary = "API-095 푸시 토큰 등록",
		description = "로그인 뒤 FCM 기기 토큰을 등록한다. 같은 토큰을 다시 보내도 성공하고, 다른 계정의 토큰이면 지금 계정으로 옮긴다.",
		security = @SecurityRequirement(name = "bearerAuth"))
	@PutMapping
	public ResponseEntity<ApiResponseBody<Void>> register(@AuthenticationPrincipal AuthenticatedUser user,
		@RequestBody PushTokenRegisterRequest request) {
		String token = request.toToken();
		pushTokenService.register(user.userId(), token, request.toPlatform());
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse());
	}

	@Operation(summary = "API-096 푸시 토큰 삭제",
		description = "로그아웃(API-007) 전에 현재 기기 토큰을 지운다. 등록된 토큰이 없어도 성공한다.",
		security = @SecurityRequirement(name = "bearerAuth"))
	@DeleteMapping
	public ResponseEntity<ApiResponseBody<Void>> delete(@AuthenticationPrincipal AuthenticatedUser user,
		@RequestBody PushTokenDeleteRequest request) {
		pushTokenService.delete(user.userId(), request.toToken());
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse());
	}
}
