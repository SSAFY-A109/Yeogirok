package com.ssafy.yeogirok.domain.place.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.place.dto.PlaceSearchResponse;
import com.ssafy.yeogirok.domain.place.service.PlaceSearchService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PlaceController {

	private final PlaceSearchService placeSearchService;

	/** 로그인 사용자만 호출한다(SecurityConfig 의 ROLE_USER). 여행방 검색 담기(W12)가 쓴다. */
	@Operation(summary = "API-026 여행지 검색")
	@GetMapping("/api/v1/places")
	public ResponseEntity<ApiResponseBody<PlaceSearchResponse>> search(
		@Parameter(description = "검색어(앞뒤 공백 제외 1~100자)") @RequestParam(required = false) String q,
		@Parameter(description = "법정동 시·도 2자리 또는 시·군·구 5자리") @RequestParam(required = false) String regionCode,
		@Parameter(description = "다음 쪽 커서. 받은 때와 같은 limit 로 보낸다(다르면 C006)") @RequestParam(required = false) String cursor,
		@Parameter(description = "한 쪽 개수. 기본 20, 최대 50") @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			placeSearchService.search(q, regionCode, cursor, limit)));
	}
}
