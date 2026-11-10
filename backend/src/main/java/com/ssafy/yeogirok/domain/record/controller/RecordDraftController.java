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

import com.ssafy.yeogirok.domain.record.draft.RecordDraftService;
import com.ssafy.yeogirok.domain.record.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.record.dto.DraftCreateRequest;
import com.ssafy.yeogirok.domain.record.dto.DraftJobResponse;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/** 여행 기록 통합 초안 API(064·065). */
@RestController
@RequestMapping("/api/v1/records/{recordId}/drafts")
@RequiredArgsConstructor
public class RecordDraftController {

	/** 멱등키 scope. 대상은 기록 ID 다. */
	static final String CREATE_SCOPE = "record-draft-create";

	private final RecordDraftService recordDraftService;
	private final IdempotencyService idempotencyService;

	@Operation(summary = "API-064 사진·영수증·계획 통합 기록 초안 생성")
	@PostMapping
	public ResponseEntity<ApiResponseBody<DraftJobResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long recordId,
		@RequestBody DraftCreateRequest request) {
		DraftCreateCommand command = request.toCommand();
		DraftJobResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), CREATE_SCOPE, recordId.toString(), idempotencyKey),
			command,
			() -> recordDraftService.create(user.userId(), recordId, command),
			DraftJobResponse::draftKey,
			draftKey -> recordDraftService.created(user.userId(), recordId, draftKey));
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponseUtil.createSuccessResponse(response));
	}
}
