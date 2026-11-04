package com.ssafy.yeogirok.domain.statistics;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.statistics.dto.VisitStatisticsResponse;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class VisitStatisticsController {

	private final VisitStatisticsService visitStatisticsService;

	/** 로그인 사용자만 호출한다(SecurityConfig 의 ROLE_USER). 결과는 사용자와 상관없는 공개 집계다. */
	@Operation(summary = "API-046 카테고리별 방문 분포")
	@GetMapping("/api/v1/visit-statistics")
	public ResponseEntity<ApiResponseBody<VisitStatisticsResponse>> getStatistics(
		@Parameter(description = "west,south,east,north") @RequestParam String bbox,
		@RequestParam double zoom,
		@RequestParam(required = false) String category,
		@RequestParam(required = false) LocalDate from,
		@RequestParam(required = false) LocalDate to,
		@Parameter(description = "VISIT_COUNT 만 허용, 생략 시 VISIT_COUNT") @RequestParam(required = false) String metric) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			visitStatisticsService.getStatistics(bbox, zoom, category, from, to, metric)));
	}
}
