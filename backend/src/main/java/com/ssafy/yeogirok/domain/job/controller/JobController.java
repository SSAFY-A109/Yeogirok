package com.ssafy.yeogirok.domain.job.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.job.dto.JobResponse;
import com.ssafy.yeogirok.domain.job.service.JobService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class JobController {

	private final JobService jobService;

	@Operation(summary = "API-011 비동기 작업 상태 조회")
	@GetMapping("/jobs/{jobId}")
	public ResponseEntity<ApiResponseBody<JobResponse>> get(@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable String jobId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(jobService.get(user.userId(), jobId)));
	}
}
