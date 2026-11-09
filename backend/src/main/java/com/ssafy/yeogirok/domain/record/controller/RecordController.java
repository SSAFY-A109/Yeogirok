package com.ssafy.yeogirok.domain.record.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.record.dto.RecordCreateRequest;
import com.ssafy.yeogirok.domain.record.dto.RecordCreateResponse;
import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/** 여행 기록 API. 명세 {@code specs/record-api.md}. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecordController {

	/** 멱등키 scope. 대상은 방 ID 다. */
	static final String CREATE_SCOPE = "record-create";

	private final RecordCreateService recordCreateService;
	private final IdempotencyService idempotencyService;

	@Operation(summary = "API-057 여행방에서 기록 공간 생성")
	@PostMapping("/rooms/{roomId}/records")
	public ResponseEntity<ApiResponseBody<RecordCreateResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long roomId,
		@RequestBody(required = false) RecordCreateRequest request) {
		String title = request == null ? null : request.toTitle();
		RecordCreateResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), CREATE_SCOPE, roomId.toString(), idempotencyKey),
			title == null ? "" : title,
			() -> recordCreateService.create(user.userId(), roomId, title),
			RecordCreateResponse::recordId,
			id -> recordCreateService.getCreated(user.userId(), roomId, Long.parseLong(id)));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}
}
