package com.ssafy.yeogirok.domain.schedule.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.schedule.draft.ScheduleDraftService;
import com.ssafy.yeogirok.domain.schedule.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.schedule.dto.DraftCreateRequest;
import com.ssafy.yeogirok.domain.schedule.dto.DraftJobResponse;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/** 일정 초안(API-037·038·084)과 최종 저장(API-039). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScheduleDraftController {

	/** 멱등키 scope. 대상은 방 ID 다. */
	static final String CREATE_SCOPE = "schedule-draft-create";

	private final ScheduleDraftService scheduleDraftService;
	private final IdempotencyService idempotencyService;

	@Operation(summary = "API-037 방문 순서·이동 시간 초안 생성")
	@PostMapping("/rooms/{roomId}/schedule-drafts")
	public ResponseEntity<ApiResponseBody<DraftJobResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long roomId,
		@RequestBody DraftCreateRequest request) {
		DraftCreateCommand command = request.toCommand();
		DraftJobResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), CREATE_SCOPE, roomId.toString(), idempotencyKey),
			command,
			() -> scheduleDraftService.create(user.userId(), roomId, command),
			DraftJobResponse::draftKey,
			scheduleDraftService::created);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponseUtil.createSuccessResponse(response));
	}
}
