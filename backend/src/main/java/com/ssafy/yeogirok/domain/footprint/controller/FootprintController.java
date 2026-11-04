package com.ssafy.yeogirok.domain.footprint.controller;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintListResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintUpdateRequest;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintUpdateResponse;
import com.ssafy.yeogirok.domain.footprint.service.FootprintService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.CurrentUserId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FootprintController {

	private final FootprintService footprintService;

	@Operation(summary = "API-042 내 발자취 목록")
	@GetMapping("/users/me/footprints")
	public ResponseEntity<ApiResponseBody<FootprintListResponse>> getMyFootprints(
		@CurrentUserId Long userId,
		@Parameter(description = "west,south,east,north") @RequestParam(required = false) String bbox,
		@RequestParam(required = false) LocalDate from,
		@RequestParam(required = false) LocalDate to,
		@RequestParam(required = false) String category,
		@RequestParam(required = false) String cursor,
		@Parameter(description = "기본 20, 최대 50") @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			footprintService.getMyFootprints(userId, bbox, from, to, category, cursor, limit)));
	}

	@Operation(summary = "API-043 내 방명록 상세")
	@GetMapping("/footprints/{footprintId}")
	public ResponseEntity<ApiResponseBody<FootprintDetailResponse>> getFootprint(
		@CurrentUserId Long userId, @PathVariable Long footprintId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			footprintService.getFootprint(userId, footprintId)));
	}

	@Operation(summary = "API-044 내 방명록 본문 수정")
	@PatchMapping("/footprints/{footprintId}")
	public ResponseEntity<ApiResponseBody<FootprintUpdateResponse>> updateBody(
		@CurrentUserId Long userId, @PathVariable Long footprintId, @RequestBody FootprintUpdateRequest request) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			footprintService.updateBody(userId, footprintId, request.version(), request.body())));
	}
}
