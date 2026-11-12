package com.ssafy.yeogirok.domain.course.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.course.dto.CourseDetailResponse;
import com.ssafy.yeogirok.domain.course.dto.CourseInput;
import com.ssafy.yeogirok.domain.course.dto.CoursePreviewResponse;
import com.ssafy.yeogirok.domain.course.dto.CoursePublicationResponse;
import com.ssafy.yeogirok.domain.course.dto.CourseRequest;
import com.ssafy.yeogirok.domain.course.dto.CourseSearchCondition;
import com.ssafy.yeogirok.domain.course.dto.CourseSearchResponse;
import com.ssafy.yeogirok.domain.course.dto.MainCourseListResponse;
import com.ssafy.yeogirok.domain.course.dto.MyCourseListResponse;
import com.ssafy.yeogirok.domain.course.service.CourseDetailService;
import com.ssafy.yeogirok.domain.course.service.CoursePreviewService;
import com.ssafy.yeogirok.domain.course.service.CoursePublishService;
import com.ssafy.yeogirok.domain.course.service.CourseSearchService;
import com.ssafy.yeogirok.domain.course.service.CourseUpdateService;
import com.ssafy.yeogirok.domain.course.service.MainCourseService;
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
	private final CourseUpdateService courseUpdateService;
	private final CourseDetailService courseDetailService;
	private final CourseSearchService courseSearchService;
	private final MainCourseService mainCourseService;
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

	/** 멱등키를 받지 않는다(노션 075, version 검사가 중복 저장을 막는다). */
	@Operation(summary = "API-075 공개할 코스 수정·공개 해제")
	@PatchMapping("/courses/{courseId}")
	public ResponseEntity<ApiResponseBody<CoursePublicationResponse>> update(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long courseId,
		@RequestBody CourseRequest request) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			courseUpdateService.update(user.userId(), courseId, request.toUpdate())));
	}

	@Operation(summary = "API-016 공개된 코스 상세 조회")
	@GetMapping("/courses/{courseId}")
	public ResponseEntity<ApiResponseBody<CourseDetailResponse>> getDetail(@PathVariable Long courseId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(courseDetailService.getDetail(courseId)));
	}

	@Operation(summary = "API-014 공개된 코스 검색")
	@GetMapping("/courses")
	public ResponseEntity<ApiResponseBody<CourseSearchResponse>> search(
		@Parameter(description = "코스 제목·방문 장소 이름(최대 100자)") @RequestParam(required = false) String q,
		@Parameter(description = "시·도 2자리 또는 시·군·구 5자리") @RequestParam(required = false) String regionCode,
		@Parameter(description = "공통 카테고리 코드") @RequestParam(required = false) String category,
		@Parameter(description = "POPULAR(기본)만") @RequestParam(required = false) String sort,
		@Parameter(description = "ALL·DAY_TRIP·ONE_NIGHT_TWO_DAYS·TWO_NIGHTS_OR_MORE") @RequestParam(required = false)
		String duration,
		@Parameter(description = "CAR·TRANSIT") @RequestParam(required = false) String transportMode,
		@RequestParam(required = false) String cursor,
		@Parameter(description = "한 번에 받을 개수. 기본 20, 최대 50") @RequestParam(required = false) Integer limit) {
		CourseSearchCondition condition = CourseSearchCondition.of(q, regionCode, category, sort, duration, transportMode);
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			courseSearchService.search(condition, cursor, limit)));
	}

	/** 고정 경로라 {@code /courses/{courseId}}(016)보다 먼저 잡힌다. */
	@Operation(summary = "API-015 메인 추천 코스 조회")
	@GetMapping("/courses/recommendations")
	public ResponseEntity<ApiResponseBody<MainCourseListResponse>> recommend(
		@Parameter(description = "공통 카테고리 코드") @RequestParam(required = false) String category,
		@Parameter(description = "시·도 2자리 또는 시·군·구 5자리") @RequestParam(required = false) String regionCode,
		@Parameter(description = "다음 페이지 커서(최초 조회 시각을 담아 같은 30일 구간을 쓴다)") @RequestParam(required = false)
		String cursor,
		@Parameter(description = "한 번에 받을 개수. 기본 20, 최대 50") @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			mainCourseService.recommend(category, regionCode, cursor, limit)));
	}
}
