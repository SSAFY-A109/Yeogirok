package com.ssafy.yeogirok.domain.category.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.category.dto.CategoryListResponse;
import com.ssafy.yeogirok.domain.category.service.CategoryService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CategoryController {

	private final CategoryService categoryService;

	/** 로그인 사용자만 호출한다(SecurityConfig 의 ROLE_USER). 발자취 등록·메인·여행방 추천이 같은 목록을 쓴다. */
	@Operation(summary = "API-079 여행 컨셉 카테고리 목록 조회")
	@GetMapping("/api/v1/categories")
	public ResponseEntity<ApiResponseBody<CategoryListResponse>> getCategories() {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(categoryService.getCategories()));
	}
}
