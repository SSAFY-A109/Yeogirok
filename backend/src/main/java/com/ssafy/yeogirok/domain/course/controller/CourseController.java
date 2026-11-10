package com.ssafy.yeogirok.domain.course.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.course.dto.CoursePreviewResponse;
import com.ssafy.yeogirok.domain.course.dto.CourseRequest;
import com.ssafy.yeogirok.domain.course.service.CoursePreviewService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/** 코스 공개 API(072~075). courseId 는 기록 ID 다. 명세 {@code specs/course-publish-api.md}. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourseController {

	private final CoursePreviewService coursePreviewService;

	@Operation(summary = "API-072 공개할 코스 미리보기")
	@PostMapping("/records/{recordId}/publication-preview")
	public ResponseEntity<ApiResponseBody<CoursePreviewResponse>> preview(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long recordId,
		@RequestBody CourseRequest request) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			coursePreviewService.preview(user.userId(), recordId, request.toPreview())));
	}
}
