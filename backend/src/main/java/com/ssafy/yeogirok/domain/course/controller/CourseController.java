package com.ssafy.yeogirok.domain.course.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.course.dto.CourseInput;
import com.ssafy.yeogirok.domain.course.dto.CoursePreviewResponse;
import com.ssafy.yeogirok.domain.course.dto.CoursePublicationResponse;
import com.ssafy.yeogirok.domain.course.dto.CourseRequest;
import com.ssafy.yeogirok.domain.course.dto.MyCourseListResponse;
import com.ssafy.yeogirok.domain.course.service.CoursePreviewService;
import com.ssafy.yeogirok.domain.course.service.CoursePublishService;
import com.ssafy.yeogirok.domain.course.service.MyCourseQueryService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

/** 코스 공개 API(072~075). courseId 는 기록 ID 다. 명세 {@code specs/course-publish-api.md}. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourseController {

	static final String PUBLISH_SCOPE = "course-publish";

	private final CoursePreviewService coursePreviewService;
	private final CoursePublishService coursePublishService;
	private final MyCourseQueryService myCourseQueryService;
	private final IdempotencyService idempotencyService;

	@Operation(summary = "API-072 공개할 코스 미리보기")
	@PostMapping("/records/{recordId}/publication-preview")
	public ResponseEntity<ApiResponseBody<CoursePreviewResponse>> preview(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long recordId,
		@RequestBody CourseRequest request) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			coursePreviewService.preview(user.userId(), recordId, request.toPreview())));
	}

	@Operation(summary = "API-073 확인한 여행 기록을 공개 코스로 게시")
	@PostMapping("/records/{recordId}/publications")
	public ResponseEntity<ApiResponseBody<CoursePublicationResponse>> publish(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long recordId,
		@RequestBody CourseRequest request) {
		CourseInput input = request.toPublish();
		CoursePublicationResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), PUBLISH_SCOPE, recordId.toString(), idempotencyKey),
			input,
			() -> coursePublishService.publish(user.userId(), recordId, input),
			CoursePublicationResponse::courseId,
			resultId -> coursePublishService.getPublished(user.userId(), recordId));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}

	@Operation(summary = "API-074 참여 기록의 공개 코스 목록")
	@GetMapping("/users/me/courses")
	public ResponseEntity<ApiResponseBody<MyCourseListResponse>> getMine(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestParam(required = false) String cursor,
		@Parameter(description = "한 번에 받을 개수. 기본 20, 최대 50") @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			myCourseQueryService.getMine(user.userId(), cursor, limit)));
	}
}
