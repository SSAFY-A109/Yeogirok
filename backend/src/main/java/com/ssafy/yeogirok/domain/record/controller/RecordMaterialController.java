package com.ssafy.yeogirok.domain.record.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.record.dto.MaterialLinkCommand;
import com.ssafy.yeogirok.domain.record.dto.MaterialLinkRequest;
import com.ssafy.yeogirok.domain.record.dto.MaterialLinkResponse;
import com.ssafy.yeogirok.domain.record.dto.MaterialListResponse;
import com.ssafy.yeogirok.domain.record.dto.MaterialReviewCommand;
import com.ssafy.yeogirok.domain.record.dto.MaterialReviewRequest;
import com.ssafy.yeogirok.domain.record.dto.MaterialReviewResponse;
import com.ssafy.yeogirok.domain.record.material.MaterialLinkService;
import com.ssafy.yeogirok.domain.record.material.MaterialQueryService;
import com.ssafy.yeogirok.domain.record.material.MaterialReviewService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.request.Uuids;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/** 여행 기록 영수증·결제 자료 API(062·063·091·092). */
@RestController
@RequestMapping("/api/v1/records/{recordId}/materials")
@RequiredArgsConstructor
public class RecordMaterialController {

	/** 멱등키 scope. 대상은 기록 ID 다. */
	static final String LINK_SCOPE = "record-material-link";
	/** 멱등키 scope. 대상은 "기록 ID-자료 ID" 다. */
	static final String REVIEW_SCOPE = "record-material-review";

	private final MaterialLinkService materialLinkService;
	private final MaterialQueryService materialQueryService;
	private final MaterialReviewService materialReviewService;
	private final IdempotencyService idempotencyService;

	@Operation(summary = "API-062 영수증·결제 자료 연결")
	@PostMapping
	public ResponseEntity<ApiResponseBody<MaterialLinkResponse>> link(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long recordId,
		@RequestBody MaterialLinkRequest request) {
		MaterialLinkCommand command = request.toCommand();
		MaterialLinkResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), LINK_SCOPE, recordId.toString(), idempotencyKey),
			command,
			() -> materialLinkService.link(user.userId(), recordId, command),
			MaterialLinkResponse::materialId,
			materialId -> materialLinkService.getLinked(user.userId(), recordId, materialId));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}

	@Operation(summary = "API-063 등록 자료·추출 결과 조회")
	@GetMapping
	public ResponseEntity<ApiResponseBody<MaterialListResponse>> list(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long recordId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(materialQueryService.list(user.userId(),
			recordId)));
	}

	@Operation(summary = "API-091 영수증 OCR 결과 보정·확인")
	@PatchMapping("/{materialId}/review")
	public ResponseEntity<ApiResponseBody<MaterialReviewResponse>> review(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long recordId, @PathVariable String materialId,
		@RequestBody MaterialReviewRequest request) {
		Uuids.require(materialId, "materialId");
		MaterialReviewCommand command = request.toCommand();
		MaterialReviewResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), REVIEW_SCOPE, recordId + "-" + materialId, idempotencyKey),
			command,
			() -> materialReviewService.review(user.userId(), recordId, materialId, command),
			MaterialReviewResponse::materialId,
			id -> materialReviewService.getReviewed(user.userId(), recordId, id));
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(response));
	}
}
