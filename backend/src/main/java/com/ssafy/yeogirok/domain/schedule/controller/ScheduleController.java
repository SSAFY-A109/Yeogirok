package com.ssafy.yeogirok.domain.schedule.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.schedule.dto.ScheduleResponse;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateRequest;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateResponse;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/** 여행방 확정 일정 조회(API-036)·직접 편집(API-040). 초안 흐름(037·038·084·039)은 {@link ScheduleDraftController}. */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/schedule")
@RequiredArgsConstructor
public class ScheduleController {

	private final ScheduleService scheduleService;

	@Operation(summary = "API-036 여행방 일정 조회")
	@GetMapping
	public ResponseEntity<ApiResponseBody<ScheduleResponse>> getSchedule(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			scheduleService.getSchedule(user.userId(), roomId)));
	}

	@Operation(summary = "API-040 일정 수동 편집·순서 고정")
	@PatchMapping
	public ResponseEntity<ApiResponseBody<ScheduleUpdateResponse>> update(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId,
		@RequestBody ScheduleUpdateRequest request) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			scheduleService.update(user.userId(), roomId, request.toCommand())));
	}
}
