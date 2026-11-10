package com.ssafy.yeogirok.domain.chat.controller;

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

import com.ssafy.yeogirok.domain.chat.dto.ChatMessagePage;
import com.ssafy.yeogirok.domain.chat.dto.ChatReadRequest;
import com.ssafy.yeogirok.domain.chat.dto.ChatReadResponse;
import com.ssafy.yeogirok.domain.chat.dto.ChatTicketRequest;
import com.ssafy.yeogirok.domain.chat.dto.ChatTicketResponse;
import com.ssafy.yeogirok.domain.chat.service.ChatMessageService;
import com.ssafy.yeogirok.domain.chat.service.ChatReadService;
import com.ssafy.yeogirok.domain.chat.service.ChatTicketService;
import com.ssafy.yeogirok.global.idempotency.IdempotencyKey;
import com.ssafy.yeogirok.global.idempotency.IdempotencyService;
import com.ssafy.yeogirok.global.response.ApiResponseBody;
import com.ssafy.yeogirok.global.response.ApiResponseUtil;
import com.ssafy.yeogirok.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

/**
 * 채팅 REST(API-076·077·088). 메시지 전송·실시간 수신은 REST 가 아니라 {@code /ws/v1} WebSocket 이다
 * ({@link com.ssafy.yeogirok.domain.chat.realtime.ChatWebSocketHandler}).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

	static final String TICKET_SCOPE = "realtime-ticket";

	private final ChatMessageService chatMessageService;
	private final ChatReadService chatReadService;
	private final ChatTicketService chatTicketService;
	private final IdempotencyService idempotencyService;

	@Operation(summary = "API-076 여행방 채팅 이력 조회")
	@GetMapping("/rooms/{roomId}/messages")
	public ResponseEntity<ApiResponseBody<ChatMessagePage>> getMessages(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId,
		@Parameter(description = "이전 이력 기준 ID(양의 정수)") @RequestParam(required = false) Long beforeMessageId,
		@Parameter(description = "재연결 복구: 이 ID 이후") @RequestParam(required = false) Long afterMessageId,
		@Parameter(description = "재연결 복구 상한. 첫 응답의 replayThroughMessageId 를 유지")
		@RequestParam(required = false) Long throughMessageId,
		@Parameter(description = "기본 20, 최대 50") @RequestParam(required = false) Integer limit) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(chatMessageService.getMessages(user.userId(),
			roomId, beforeMessageId, afterMessageId, throughMessageId, limit)));
	}

	@Operation(summary = "API-088 여행방 채팅 읽은 위치 갱신")
	@PatchMapping("/rooms/{roomId}/messages/read")
	public ResponseEntity<ApiResponseBody<ChatReadResponse>> markRead(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long roomId,
		@RequestBody ChatReadRequest request) {
		return ResponseEntity.ok(ApiResponseUtil.createSuccessResponse(
			chatReadService.markRead(user.userId(), roomId, request.toLastReadMessageId())));
	}

	@Operation(summary = "API-077 실시간 연결 티켓 발급")
	@PostMapping("/realtime/tickets")
	public ResponseEntity<ApiResponseBody<ChatTicketResponse>> issueTicket(
		@AuthenticationPrincipal AuthenticatedUser user,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody ChatTicketRequest request) {
		long roomId = request.toRoomId();
		ChatTicketResponse response = idempotencyService.execute(
			new IdempotencyKey(user.userId(), TICKET_SCOPE, Long.toString(roomId), idempotencyKey),
			roomId,
			() -> chatTicketService.issue(user.userId(), roomId),
			ChatTicketService::resultIdOf,
			resultId -> chatTicketService.getIssued(user.userId(), roomId, resultId));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseUtil.createSuccessResponse(response));
	}
}
