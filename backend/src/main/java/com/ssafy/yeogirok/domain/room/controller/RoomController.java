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

import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateRequest;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomDetailResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomInvitationAcceptRequest;
import com.ssafy.yeogirok.domain.room.dto.RoomInvitationResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomJoinResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomListResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateRequest;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateResponse;
import com.ssafy.yeogirok.domain.room.service.RoomInvitationService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RoomController {

	/** 멱등키 scope. 대상이 없는 요청은 "-", 방에 대한 요청은 방 ID 다. */
	static final String CREATE_SCOPE = "room-create";
	static final String INVITATION_SCOPE = "room-invitation-create";
	static final String ACCEPT_SCOPE = "room-invitation-accept";

	private final RoomService roomService;
	private final RoomInvitationService roomInvitationService;
	private final IdempotencyService idempotencyService;

	@Operation(summary = "API-020 참여 여행방 목록")
	@GetMapping("/rooms")
	public ResponseEntity<ApiResponseBody<RoomListResponse>> getMyRooms(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestParam(required = false) String cursor,
		@Parameter(description = "기본 20, 최대 50") @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			roomService.getMyRooms(user.userId(), cursor, limit)));
	}

	@Operation(summary = "API-021 여행방 생성")
	@PostMapping("/rooms")
	public ResponseEntity<ApiResponseBody<RoomCreateResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody RoomCreateRequest request) {
		RoomCreateCommand command = request.toCommand();
		RoomCreateResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), CREATE_SCOPE, "-", idempotencyKey),
			command,
			() -> roomService.create(user.userId(), command),
			RoomCreateResponse::id,
			id -> roomService.getCreated(user.userId(), Long.parseLong(id)));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}

	@Operation(summary = "API-022 여행방 상세")
	@GetMapping("/rooms/{roomId}")
	public ResponseEntity<ApiResponseBody<RoomDetailResponse>> getRoom(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(roomService.getRoom(user.userId(), roomId)));
	}

	@Operation(summary = "API-023 여행방 정보·이동 수단 수정")
	@PatchMapping("/rooms/{roomId}")
	public ResponseEntity<ApiResponseBody<RoomUpdateResponse>> update(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId,
		@RequestBody RoomUpdateRequest request) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			roomService.update(user.userId(), roomId, request.toCommand())));
	}

	/** 처음 만들면 201, 이미 있는 링크면 200. 같은 멱등키 재요청은 기존 링크라 200 이다. */
	@Operation(summary = "API-024 여행방 초대 링크 생성·조회")
	@PostMapping("/rooms/{roomId}/invitations")
	public ResponseEntity<ApiResponseBody<RoomInvitationResponse>> createInvitation(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@PathVariable Long roomId) {
		RoomInvitationResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), INVITATION_SCOPE, roomId.toString(), idempotencyKey),
			roomId,
			() -> roomInvitationService.getOrCreateInvitation(user.userId(), roomId),
			RoomInvitationResponse::invitationId,
			id -> roomInvitationService.getOrCreateInvitation(user.userId(), Long.parseLong(id)));
		return ResponseEntity.status(response.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(ApiResponseUtil.createSuccessResponse(response));
	}

	@Operation(summary = "API-025 초대 링크로 여행방 참여")
	@PostMapping("/room-invitations/accept")
	public ResponseEntity<ApiResponseBody<RoomJoinResponse>> accept(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody RoomInvitationAcceptRequest request) {
		String inviteToken = request.inviteToken();
		RoomJoinResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), ACCEPT_SCOPE, "-", idempotencyKey),
			inviteToken,
			() -> roomInvitationService.accept(user.userId(), inviteToken),
			RoomJoinResponse::roomId,
			id -> roomInvitationService.getJoined(user.userId(), Long.parseLong(id)));
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(response));
	}

	@Operation(summary = "API-087 여행방 나가기")
	@DeleteMapping("/rooms/{roomId}/members/me")
	public ResponseEntity<ApiResponseBody<Void>> leave(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId) {
		roomService.leave(user.userId(), roomId);
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse());
	}
}
