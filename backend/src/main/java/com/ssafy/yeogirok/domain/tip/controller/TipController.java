package com.ssafy.yeogirok.domain.tip.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.tip.dto.TipCreateRequest;
import com.ssafy.yeogirok.domain.tip.dto.TipCreateRequest.TipCreateCommand;
import com.ssafy.yeogirok.domain.tip.dto.TipCreateResponse;
import com.ssafy.yeogirok.domain.tip.dto.TipReactionRequest;
import com.ssafy.yeogirok.domain.tip.dto.TipReactionResponse;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchRequest;
import com.ssafy.yeogirok.domain.tip.dto.TipSearchResponse;
import com.ssafy.yeogirok.domain.tip.dto.TipStatusResponse;
import com.ssafy.yeogirok.domain.tip.service.TipCreateService;
import com.ssafy.yeogirok.domain.tip.service.TipReactionService;
import com.ssafy.yeogirok.domain.tip.service.TipSearchService;
import com.ssafy.yeogirok.domain.tip.service.TipStatusService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tips")
@RequiredArgsConstructor
public class TipController {

	/** 멱등키 scope. 새로 만드는 요청이라 대상은 "-" 다. */
	static final String CREATE_SCOPE = "tip-create";

	private final TipCreateService tipCreateService;
	private final TipSearchService tipSearchService;
	private final TipReactionService tipReactionService;
	private final TipStatusService tipStatusService;
	private final IdempotencyService idempotencyService;

	/** 형식 검사는 멱등키 처리 전, 값 검사는 처리 안에서 한다. 재요청은 유효 시각이 지나도 처음 결과를 준다. */
	@Operation(summary = "API-049 AR 팁 등록 작업 생성")
	@PostMapping
	public ResponseEntity<ApiResponseBody<TipCreateResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody TipCreateRequest request) {
		TipCreateCommand command = request.toCommand();
		TipCreateResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), CREATE_SCOPE, "-", idempotencyKey),
			command,
			() -> tipCreateService.create(user.userId(), command),
			TipCreateResponse::tipId,
			id -> tipCreateService.getCreated(user.userId(), Long.parseLong(id)));
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponseUtil.createSuccessResponse(response));
	}

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

	@Operation(summary = "API-050 내 팁 등록·게시 상태 조회")
	@GetMapping("/{tipId}/status")
	public ResponseEntity<ApiResponseBody<TipStatusResponse>> getStatus(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long tipId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(tipStatusService.getStatus(user.userId(), tipId)));
	}
}
