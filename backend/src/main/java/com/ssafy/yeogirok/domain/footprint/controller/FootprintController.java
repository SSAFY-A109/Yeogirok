package com.ssafy.yeogirok.domain.footprint.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;
import com.ssafy.yeogirok.domain.footprint.service.FootprintService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.CurrentUserId;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/footprints")
@RequiredArgsConstructor
public class FootprintController {

	private final FootprintService footprintService;

	@Operation(summary = "API-043 내 방명록 상세")
	@GetMapping("/{footprintId}")
	public ResponseEntity<ApiResponseBody<FootprintDetailResponse>> getFootprint(
		@CurrentUserId Long userId, @PathVariable Long footprintId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			footprintService.getFootprint(userId, footprintId)));
	}
}
