package com.ssafy.yeogirok.domain.region.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.region.dto.RegionListResponse;
import com.ssafy.yeogirok.domain.region.service.RegionService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RegionController {

	private final RegionService regionService;

	/** 로그인 사용자만 호출한다(SecurityConfig 의 ROLE_USER). 추천 조건(W10)의 시·도→시·군·구 선택에 쓴다. */
	@Operation(summary = "API-078 추천 지역 계층 목록 조회")
	@GetMapping("/api/v1/regions")
	public ResponseEntity<ApiResponseBody<RegionListResponse>> getRegions(
		@Parameter(description = "시·도 code. 없으면 시·도 목록, 있으면 그 시·도의 시·군·구 목록")
		@RequestParam(required = false) String parentCode) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(regionService.getRegions(parentCode)));
	}
}
