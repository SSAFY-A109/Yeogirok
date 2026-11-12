package com.ssafy.yeogirok.domain.course.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.course.dto.PopularPlaceListResponse;
import com.ssafy.yeogirok.domain.course.service.PopularPlaceService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

/** 메인 화면 API 중 {@code /main} 경로(093). 추천 코스(015)는 {@code /courses} 경로라 {@link CourseController} 에 있다. */
@RestController
@RequestMapping("/api/v1/main")
@RequiredArgsConstructor
public class MainController {

	private final PopularPlaceService popularPlaceService;

	@Operation(summary = "API-093 메인 인기 장소 조회")
	@GetMapping("/popular-places")
	public ResponseEntity<ApiResponseBody<PopularPlaceListResponse>> popularPlaces(
		@Parameter(description = "공통 카테고리 코드") @RequestParam(required = false) String category) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(popularPlaceService.popular(category)));
	}
}
