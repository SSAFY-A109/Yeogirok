package com.ssafy.yeogirok.domain.file.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.file.dto.UploadCreateCommand;
import com.ssafy.yeogirok.domain.file.dto.UploadCreateRequest;
import com.ssafy.yeogirok.domain.file.dto.UploadCreateResponse;
import com.ssafy.yeogirok.domain.file.service.UploadService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

	/** 멱등키 scope. 새로 만드는 요청이라 대상은 "-" 다. */
	static final String CREATE_SCOPE = "upload-create";

	private final UploadService uploadService;
	private final IdempotencyService idempotencyService;

	/** 같은 키 재요청은 세션 상태에 따라 같은 계약(URL 재서명)·M011·M013 이다. */
	@Operation(summary = "API-012 사진·영수증 업로드 URL 발급")
	@PostMapping
	public ResponseEntity<ApiResponseBody<UploadCreateResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody UploadCreateRequest request) {
		UploadCreateCommand command = request.toCommand();
		UploadCreateResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), CREATE_SCOPE, "-", idempotencyKey),
			command,
			() -> uploadService.create(user.userId(), command),
			UploadCreateResponse::uploadId,
			uploadId -> uploadService.getCreated(user.userId(), uploadId));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}
}
