package com.ssafy.yeogirok.domain.room.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateRequest;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceAddCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceAddRequest;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceAddResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceListResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceRemoveResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceRestoreRequest;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceRestoreResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceState;
import com.ssafy.yeogirok.domain.room.service.RoomPlaceAddService;
import com.ssafy.yeogirok.domain.room.service.RoomPlaceService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

/** 여행방 후보 장소. API-029 는 추천 담기(AI)만 있고 검색 담기·API-030(코스 가져오기)은 아직 없다({@link RoomPlaceService} TODO). */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}")
@RequiredArgsConstructor
public class RoomPlaceController {

	/** 멱등키 scope. 대상은 방 ID 다. */
	static final String CUSTOM_PLACE_SCOPE = "room-custom-place-create";
	static final String ADD_PLACES_SCOPE = "room-place-add";

	private final RoomPlaceService roomPlaceService;
	private final RoomPlaceAddService roomPlaceAddService;
	private final IdempotencyService idempotencyService;

	@Operation(summary = "API-028 여행방 후보 장소 목록 조회")
	@GetMapping("/places")
	public ResponseEntity<ApiResponseBody<RoomPlaceListResponse>> getPlaces(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId,
		@Parameter(description = "ACTIVE(기본)·REMOVED·ALL") @RequestParam(required = false) String state) {
		RoomPlaceState placeState = RoomPlaceState.parse(state);
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			roomPlaceService.getPlaces(user.userId(), roomId, placeState)));
	}

	@Operation(summary = "API-029 검색·AI 추천 장소 담기 (지금은 AI 만)")
	@PostMapping("/places")
	public ResponseEntity<ApiResponseBody<RoomPlaceAddResponse>> addPlaces(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long roomId,
		@RequestBody RoomPlaceAddRequest request) {
		RoomPlaceAddCommand command = request.toCommand();
		RoomPlaceAddResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), ADD_PLACES_SCOPE, roomId.toString(), idempotencyKey),
			command,
			() -> roomPlaceAddService.add(user.userId(), roomId, command),
			RoomPlaceAddService::resultIdOf,
			id -> roomPlaceAddService.getAdded(user.userId(), roomId, RoomPlaceAddService.roomPlaceIdsOf(id)));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}

	@Operation(summary = "API-031 지도 핀으로 장소 직접 추가")
	@PostMapping("/custom-places")
	public ResponseEntity<ApiResponseBody<CustomPlaceCreateResponse>> addCustomPlace(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long roomId,
		@RequestBody CustomPlaceCreateRequest request) {
		CustomPlaceCreateCommand command = request.toCommand();
		CustomPlaceCreateResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), CUSTOM_PLACE_SCOPE, roomId.toString(), idempotencyKey),
			command,
			() -> roomPlaceService.addCustomPlace(user.userId(), roomId, command),
			created -> created.roomPlace().id(),
			id -> roomPlaceService.getAddedCustomPlace(user.userId(), roomId, Long.parseLong(id)));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}

	@Operation(summary = "API-032 담은 후보 장소 빼기")
	@DeleteMapping("/places/{roomPlaceId}")
	public ResponseEntity<ApiResponseBody<RoomPlaceRemoveResponse>> remove(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId, @PathVariable Long roomPlaceId,
		@Parameter(description = "현재 여행방 version") @RequestParam int version) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			roomPlaceService.remove(user.userId(), roomId, roomPlaceId, version)));
	}

	@Operation(summary = "API-094 삭제한 후보 장소 복원")
	@PatchMapping("/places/{roomPlaceId}")
	public ResponseEntity<ApiResponseBody<RoomPlaceRestoreResponse>> restore(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId, @PathVariable Long roomPlaceId,
		@RequestBody RoomPlaceRestoreRequest request) {
		int version = request.toVersion();
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			roomPlaceService.restore(user.userId(), roomId, roomPlaceId, version)));
	}
}
