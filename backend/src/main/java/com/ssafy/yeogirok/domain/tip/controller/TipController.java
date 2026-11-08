package com.ssafy.yeogirok.domain.tip.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.tip.dto.TipReactionRequest;
import com.ssafy.yeogirok.domain.tip.dto.TipReactionResponse;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchRequest;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchResponse;
import com.ssafy.yeogirok.domain.tip.service.TipReactionService;
import com.ssafy.yeogirok.domain.tip.service.TipSearchService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tips")
@RequiredArgsConstructor
public class TipController {

	private final TipSearchService tipSearchService;
	private final TipReactionService tipReactionService;

	/** 위치를 URL·캐시 키에 남기지 않으려고 POST 로 조회한다(노션 API-047). */
	@Operation(summary = "API-047 현장 주변 공개 팁 탐색")
	@PostMapping("/search")
	public ResponseEntity<ApiResponseBody<TipSearchResponse>> search(
		@AuthenticationPrincipal AuthenticatedUser user, @RequestBody TipSearchRequest request) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(tipSearchService.search(request.toQuery())));
	}

	@Operation(summary = "API-052 팁 좋아요·싫어요·취소")
	@PutMapping("/{tipId}/reaction")
	public ResponseEntity<ApiResponseBody<TipReactionResponse>> react(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long tipId,
		@RequestBody TipReactionRequest request) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			tipReactionService.react(user.userId(), tipId, request.toCommand())));
	}
}
