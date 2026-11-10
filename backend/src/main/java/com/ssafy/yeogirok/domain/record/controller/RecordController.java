package com.ssafy.yeogirok.domain.record.controller;

import java.util.Arrays;

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

import com.ssafy.yeogirok.domain.record.dto.RecordCreateRequest;
import com.ssafy.yeogirok.domain.record.dto.RecordCreateResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordDetailResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordListResponse;
import com.ssafy.yeogirok.domain.record.dto.StoryEditResponse;
import com.ssafy.yeogirok.domain.record.dto.StoryRequest;
import com.ssafy.yeogirok.domain.record.dto.StoryWriteResponse;
import com.ssafy.yeogirok.domain.record.dto.VisitAddCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitAddRequest;
import com.ssafy.yeogirok.domain.record.dto.VisitAddResponse;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateRequest;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateResponse;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.domain.record.service.RecordQueryService;
import com.ssafy.yeogirok.domain.record.service.StoryService;
import com.ssafy.yeogirok.domain.record.service.VisitCommandService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.request.JsonFields;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

/** 여행 기록 API. 명세 {@code specs/record-api.md}. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecordController {

	/** 멱등키 scope. 대상은 방 ID 다. */
	static final String CREATE_SCOPE = "record-create";
	/** 멱등키 scope. 대상은 기록 ID 다. */
	static final String VISIT_ADD_SCOPE = "record-visit-add";
	/** 멱등키 scope. 대상은 방문 ID 다. */
	static final String STORY_WRITE_SCOPE = "record-story-write";

	private final RecordCreateService recordCreateService;
	private final RecordQueryService recordQueryService;
	private final VisitCommandService visitCommandService;
	private final StoryService storyService;
	private final IdempotencyService idempotencyService;

	@Operation(summary = "API-057 여행방에서 기록 공간 생성")
	@PostMapping("/rooms/{roomId}/records")
	public ResponseEntity<ApiResponseBody<RecordCreateResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long roomId,
		@RequestBody(required = false) RecordCreateRequest request) {
		String title = request == null ? null : request.toTitle();
		RecordCreateResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), CREATE_SCOPE, roomId.toString(), idempotencyKey),
			title == null ? "" : title,
			() -> recordCreateService.create(user.userId(), roomId, title),
			RecordCreateResponse::recordId,
			id -> recordCreateService.getCreated(user.userId(), roomId, Long.parseLong(id)));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}

	@Operation(summary = "API-058 참여 여행 기록 목록 조회")
	@GetMapping("/records")
	public ResponseEntity<ApiResponseBody<RecordListResponse>> getList(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestParam(required = false) Long roomId,
		@Parameter(description = "DRAFT·COMPLETED") @RequestParam(required = false) String status,
		@RequestParam(required = false) String cursor,
		@Parameter(description = "한 번에 받을 개수. 기본 20, 최대 50") @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			recordQueryService.getList(user.userId(), roomId, parseStatus(status), cursor, limit)));
	}

	@Operation(summary = "API-059 여행 기록 상세 조회")
	@GetMapping("/records/{recordId}")
	public ResponseEntity<ApiResponseBody<RecordDetailResponse>> getDetail(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long recordId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			recordQueryService.getDetail(user.userId(), recordId)));
	}

	/** 형식 검사는 멱등키 처리 전에 한다. 결과 ID 는 만든 방문 ID 들을 쉼표로 이은 값이다. */
	@Operation(summary = "API-066 실제 방문 수동 추가")
	@PostMapping("/records/{recordId}/visits")
	public ResponseEntity<ApiResponseBody<VisitAddResponse>> addVisits(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long recordId,
		@RequestBody VisitAddRequest request) {
		VisitAddCommand command = request.toCommand();
		VisitAddResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), VISIT_ADD_SCOPE, recordId.toString(), idempotencyKey),
			command,
			() -> visitCommandService.add(user.userId(), recordId, command),
			added -> String.join(",", added.visitIds().stream().map(String::valueOf).toList()),
			ids -> visitCommandService.getAdded(user.userId(), recordId,
				Arrays.stream(ids.split(",")).map(Long::valueOf).toList()));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}

	@Operation(summary = "API-067 실제 방문 일정·시간·순서 수정·삭제")
	@PatchMapping("/records/{recordId}/visits")
	public ResponseEntity<ApiResponseBody<VisitUpdateResponse>> updateVisits(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long recordId,
		@RequestBody VisitUpdateRequest request) {
		VisitUpdateCommand command = request.toCommand();
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			visitCommandService.update(user.userId(), recordId, command)));
	}

	@Operation(summary = "API-069 일정별 이야기 작성")
	@PostMapping("/records/{recordId}/visits/{visitId}/stories")
	public ResponseEntity<ApiResponseBody<StoryWriteResponse>> writeStory(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long recordId, @PathVariable Long visitId,
		@RequestBody StoryRequest request) {
		String body = request.toBody();
		StoryWriteResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), STORY_WRITE_SCOPE, visitId.toString(), idempotencyKey),
			body,
			() -> storyService.write(user.userId(), recordId, visitId, body),
			StoryWriteResponse::storyId,
			id -> storyService.getWritten(user.userId(), recordId, Long.parseLong(id)));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}

	@Operation(summary = "API-070 일정별 이야기 수정")
	@PatchMapping("/records/{recordId}/visits/{visitId}/stories/{storyId}")
	public ResponseEntity<ApiResponseBody<StoryEditResponse>> editStory(
		@AuthenticationPrincipal AuthenticatedUser user,
		@PathVariable Long recordId, @PathVariable Long visitId, @PathVariable Long storyId,
		@RequestBody StoryRequest request) {
		String body = request.toBody();
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			storyService.edit(user.userId(), recordId, visitId, storyId, body)));
	}

	/** 생략이면 null, DRAFT·COMPLETED 가 아니면 C002(노션 058). */
	private static RecordStatus parseStatus(String status) {
		if (status == null) {
			return null;
		}
		try {
			return RecordStatus.valueOf(status);
		} catch (IllegalArgumentException e) {
			throw JsonFields.invalidFormat("status", "DRAFT 또는 COMPLETED 여야 합니다.");
		}
	}
}
