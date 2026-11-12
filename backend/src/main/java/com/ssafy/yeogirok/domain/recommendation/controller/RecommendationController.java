package com.ssafy.yeogirok.domain.recommendation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationCommand;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationRequest;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationResponse;
import com.ssafy.yeogirok.domain.recommendation.dto.PreferenceResponse;
import com.ssafy.yeogirok.domain.recommendation.dto.PreferenceSaveRequest;
import com.ssafy.yeogirok.domain.recommendation.service.PlaceRecommendationService;
import com.ssafy.yeogirok.domain.recommendation.service.RecommendationPreferenceService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/** 여행방 장소 추천 조건·추천(API-033·081·034). 추천은 백엔드 규칙 점수로 만든다(AI 서버 미사용). */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}")
@RequiredArgsConstructor
public class RecommendationController {

	/** 멱등키 scope. 대상은 방 ID 다. */
	static final String RECOMMENDATION_SCOPE = "room-place-recommendation";

	private final RecommendationPreferenceService preferenceService;
	private final PlaceRecommendationService recommendationService;
	private final IdempotencyService idempotencyService;

	@Operation(summary = "API-081 여행방 추천 조건 조회")
	@GetMapping("/preferences")
	public ResponseEntity<ApiResponseBody<PreferenceResponse>> getPreferences(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(preferenceService.get(user.userId(), roomId)));
	}

	@Operation(summary = "API-033 추천 조건 저장")
	@PutMapping("/preferences")
	public ResponseEntity<ApiResponseBody<PreferenceResponse>> savePreferences(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId,
		@RequestBody PreferenceSaveRequest request) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			preferenceService.save(user.userId(), roomId, request.toCommand())));
	}

	@Operation(summary = "API-034 여행지 추천 조회")
	@PostMapping("/place-recommendations")
	public ResponseEntity<ApiResponseBody<PlaceRecommendationResponse>> recommend(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long roomId,
		@RequestBody(required = false) PlaceRecommendationRequest request) {
		PlaceRecommendationCommand command = request == null ? PlaceRecommendationCommand.EMPTY : request.toCommand();
		PlaceRecommendationResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), RECOMMENDATION_SCOPE, roomId.toString(), idempotencyKey),
			command,
			() -> recommendationService.recommend(user.userId(), roomId, command),
			PlaceRecommendationResponse::recommendationRequestId,
			id -> recommendationService.getRecommended(user.userId(), roomId, Long.parseLong(id)));
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(response));
	}
}
