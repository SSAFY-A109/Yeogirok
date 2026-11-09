package com.ssafy.yeogirok.domain.tip.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.tip.dto.MyTipDetailResponse;
import com.ssafy.yeogirok.domain.tip.dto.MyTipListResponse;
import com.ssafy.yeogirok.domain.tip.service.MyTipService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users/me/tips")
@RequiredArgsConstructor
public class MyTipController {

	private final MyTipService myTipService;

	@Operation(summary = "API-053 내 팁 목록 조회")
	@GetMapping
	public ResponseEntity<ApiResponseBody<MyTipListResponse>> getMyTips(
		@AuthenticationPrincipal AuthenticatedUser user,
		@Parameter(description = "REGISTERING·PUBLISHED·FAILED") @RequestParam(required = false) String status,
		@RequestParam(required = false) String cursor,
		@Parameter(description = "기본 20, 최대 50") @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			myTipService.getMyTips(user.userId(), status, cursor, limit)));
	}

	@Operation(summary = "API-090 내 팁 본문·사진 상세 조회")
	@GetMapping("/{tipId}")
	public ResponseEntity<ApiResponseBody<MyTipDetailResponse>> getMyTip(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long tipId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(myTipService.getMyTip(user.userId(), tipId)));
	}
}
