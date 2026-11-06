package com.ssafy.yeogirok.domain.file.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.file.dto.UploadCompleteResponse;
import com.ssafy.yeogirok.domain.file.dto.UploadCreateCommand;
import com.ssafy.yeogirok.domain.file.dto.UploadCreateRequest;
import com.ssafy.yeogirok.domain.file.dto.UploadCreateResponse;
import com.ssafy.yeogirok.domain.file.service.UploadCompleteService;
import com.ssafy.yeogirok.domain.file.service.UploadService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
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

	/** 멱등키 scope. 발급은 새로 만드는 요청이라 대상이 "-", 완료는 uploadId 다. */
	static final String CREATE_SCOPE = "upload-create";
	static final String COMPLETE_SCOPE = "upload-complete";

	/** uploadId 는 서버가 만든 소문자 UUID 다. */
	private static final Pattern UPLOAD_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private final UploadService uploadService;
	private final UploadCompleteService uploadCompleteService;
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

	/** 같은 키 재요청·다른 키로 온 같은 uploadId 의 재요청 모두 READY 면 처음 결과를 준다. */
	@Operation(summary = "API-086 파일 업로드 완료 확인")
	@PostMapping("/{uploadId}/complete")
	public ResponseEntity<ApiResponseBody<UploadCompleteResponse>> complete(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable String uploadId) {
		if (!UPLOAD_ID.matcher(uploadId).matches()) {
			throw new BusinessException(ExceptionType.INVALID_VALUE_FORMAT, "uploadId: UUID 여야 합니다.");
		}
		UploadCompleteResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), COMPLETE_SCOPE, uploadId, idempotencyKey),
			uploadId,
			() -> uploadCompleteService.complete(user.userId(), uploadId),
			UploadCompleteResponse::uploadId,
			id -> uploadCompleteService.complete(user.userId(), id));
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(response));
	}
}
