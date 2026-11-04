package com.ssafy.yeogirok.domain.footprint.controller;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintCreateCommand;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintCreateRequest;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintCreateResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintDeleteResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintListResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintUpdateRequest;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintUpdateResponse;
import com.ssafy.yeogirok.domain.footprint.service.FootprintCreateService;
import com.ssafy.yeogirok.domain.footprint.service.FootprintService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FootprintController {

	/** 멱등키 scope. 새로 만드는 요청이라 대상은 "-" 다. */
	static final String CREATE_SCOPE = "footprint-create";

	private final FootprintService footprintService;
	private final FootprintCreateService footprintCreateService;
	private final IdempotencyService idempotencyService;

	/** 형식 검사는 멱등키 처리 전, 값 검사는 처리 안에서 한다. 그래서 재요청은 유효 시각이 지나도 처음 결과를 준다. */
	@Operation(summary = "API-041 발자취·방명록 저장")
	@PostMapping("/footprints")
	public ResponseEntity<ApiResponseBody<FootprintCreateResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody FootprintCreateRequest request) {
		FootprintCreateCommand command = request.toCommand();
		FootprintCreateResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), CREATE_SCOPE, "-", idempotencyKey),
			command,
			() -> footprintCreateService.create(user.userId(), command),
			FootprintCreateResponse::id,
			id -> footprintCreateService.getCreated(user.userId(), Long.parseLong(id)));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}

	@Operation(summary = "API-042 내 발자취 목록")
	@GetMapping("/users/me/footprints")
	public ResponseEntity<ApiResponseBody<FootprintListResponse>> getMyFootprints(
		@AuthenticationPrincipal AuthenticatedUser user,
		@Parameter(description = "west,south,east,north") @RequestParam(required = false) String bbox,
		@RequestParam(required = false) LocalDate from,
		@RequestParam(required = false) LocalDate to,
		@RequestParam(required = false) String category,
		@RequestParam(required = false) String cursor,
		@Parameter(description = "기본 20, 최대 50") @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			footprintService.getMyFootprints(user.userId(), bbox, from, to, category, cursor, limit)));
	}

	@Operation(summary = "API-043 내 방명록 상세")
	@GetMapping("/footprints/{footprintId}")
	public ResponseEntity<ApiResponseBody<FootprintDetailResponse>> getFootprint(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long footprintId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			footprintService.getFootprint(user.userId(), footprintId)));
	}

	@Operation(summary = "API-044 내 방명록 본문 수정")
	@PatchMapping("/footprints/{footprintId}")
	public ResponseEntity<ApiResponseBody<FootprintUpdateResponse>> updateBody(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long footprintId,
		@RequestBody FootprintUpdateRequest request) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			footprintService.updateBody(user.userId(), footprintId, request.version(), request.body())));
	}

	@Operation(summary = "API-045 내 발자취 삭제")
	@DeleteMapping("/footprints/{footprintId}")
	public ResponseEntity<ApiResponseBody<FootprintDeleteResponse>> delete(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long footprintId, @RequestParam int version) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			footprintService.delete(user.userId(), footprintId, version)));
	}
}
