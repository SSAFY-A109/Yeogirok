package com.ssafy.yeogirok.domain.chat.realtime;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.ssafy.yeogirok.domain.chat.dto.ChatMessage;
import com.ssafy.yeogirok.domain.chat.realtime.ChatTicketStore.TicketOwner;
import com.ssafy.yeogirok.domain.chat.service.ChatMessageService;
import com.ssafy.yeogirok.domain.chat.service.ChatMessageService.SendResult;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.request.JsonFields;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /ws/v1} 채팅 WebSocket(노션 API-077 계약). 프레임은 JSON 텍스트이고 STOMP 를 쓰지 않는다.
 *
 * <ol>
 *   <li>첫 프레임은 AUTH. Redis 티켓을 한 번 소모하고 계정·현재 참여·방 미삭제를 확인한 뒤 방 수신을 준비하고 READY 를
 *       보낸다. 티켓이 없거나 쓸 수 없으면 ERROR 후 연결을 닫는다.</li>
 *   <li>SEND_MESSAGE: 매번 권한을 다시 확인하고 저장한다. 커밋 뒤 보낸 사람에게 MESSAGE_ACK, 방에 MESSAGE_CREATED.</li>
 *   <li>PING: 참여·방을 다시 확인하고 PONG 에 최신 저장 ID 를 담는다.</li>
 * </ol>
 * 연결 이후 오류는 HTTP 상태가 아니라 ERROR 프레임으로 알린다. 방 없음·참여자 아님·계정 무효는 오류를 보낸 뒤 닫는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

	/** 이 오류가 나면 연결을 더 쓸 수 없어 닫는다. */
	private static final Set<ExceptionType> FATAL = Set.of(ExceptionType.ROOM_NOT_FOUND, ExceptionType.ROOM_NOT_MEMBER,
		ExceptionType.ACCESS_TOKEN_INVALID);

	private final ChatHub hub;
	private final ChatTicketStore ticketStore;
	private final ChatMessageService chatMessageService;
	private final ChatPublisher publisher;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		hub.open(session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		hub.closed(session.getId());
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		log.debug("채팅 연결 전송 오류. session={}", session.getId(), exception);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		ChatConnection connection = hub.get(session.getId());
		if (connection == null) {
			return;
		}
		connection.touch(clock.instant());
		JsonNode frame = parse(message.getPayload());

		if (!connection.isAuthenticated()) {
			authenticate(connection, frame);
			return;
		}
		if (frame == null || !frame.isObject()) {
			error(connection, null, ExceptionType.INVALID_VALUE_FORMAT, "JSON 객체 프레임이어야 합니다.");
			return;
		}
		String type = textOrNull(frame.get("type"));
		switch (type == null ? "" : type) {
			case "SEND_MESSAGE" -> sendMessage(connection, frame);
			case "PING" -> ping(connection);
			default -> error(connection, null, ExceptionType.NOT_ALLOWED_VALUE, "지원하지 않는 type 입니다: " + type);
		}
	}

	/* ── AUTH ─────────────────────────────────────────────── */

	private void authenticate(ChatConnection connection, JsonNode frame) {
		String ticket = frame != null && frame.isObject() && "AUTH".equals(textOrNull(frame.get("type")))
			? textOrNull(frame.get("ticket"))
			: null;
		if (ticket == null) {
			hub.reject(connection, null, null, ExceptionType.ACCESS_TOKEN_MISSING, "첫 프레임은 ticket 을 담은 AUTH 여야 합니다.");
			return;
		}
		Optional<TicketOwner> owner = ticketStore.consume(ticket);
		if (owner.isEmpty()) {
			hub.reject(connection, null, null, ExceptionType.ACCESS_TOKEN_INVALID, "티켓이 없거나 만료·사용되었습니다.");
			return;
		}
		Long userId = owner.get().userId();
		Long roomId = owner.get().roomId();
		try {
			chatMessageService.checkAccess(userId, roomId);
			hub.join(connection, userId, roomId);
			// 방에 묶은 뒤 한 번 더 확인한다. 첫 확인과 묶기 사이에 나가기·방 삭제가 커밋되면 그 강제 종료 알림은 아직
			// 묶이지 않은 이 연결을 지나치므로, 여기서 걸러 닫는다. 통과 전에 온 방 메시지는 연결이 잡아 두고 READY 뒤에 보낸다.
			// 최신 ID 도 구독 뒤에 읽어 READY 와 실시간 사이 빈틈이 없다.
			Long latestMessageId = chatMessageService.checkAccess(userId, roomId);
			hub.ready(connection, new ChatFrames.Ready(roomId, userId, latestMessageId));
		} catch (BusinessException e) {
			hub.reject(connection, roomId, null, e.getExceptionType(), e.getMessage());
		} catch (RuntimeException e) {
			log.error("채팅 연결 인증 중 오류. userId={}, roomId={}", userId, roomId, e);
			hub.reject(connection, roomId, null, ExceptionType.SERVER_ERROR, ExceptionType.SERVER_ERROR.getMessage());
		}
	}

	/* ── SEND_MESSAGE ────────────────────────────────────── */

	private void sendMessage(ChatConnection connection, JsonNode frame) {
		Long roomId = connection.getRoomId();
		JsonNode clientMessageIdNode = frame.get("clientMessageId");
		// 오류 프레임에 되돌려 줄 값. 판별할 수 없으면 null.
		String echoId = clientMessageIdNode != null && clientMessageIdNode.isString()
			? clientMessageIdNode.stringValue()
			: null;

		String clientMessageId;
		String body;
		try {
			long requestedRoomId = JsonFields.requiredLong(frame.get("roomId"), "roomId");
			clientMessageId = JsonFields.nonNullString(clientMessageIdNode, "clientMessageId");
			body = JsonFields.nonNullString(frame.get("body"), "body");
			if (requestedRoomId != roomId) {
				throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "roomId 가 연결된 여행방과 다릅니다.");
			}
		} catch (BusinessException e) {
			error(connection, echoId, e.getExceptionType(), e.getMessage());
			return;
		}

		SendResult result;
		try {
			result = chatMessageService.send(connection.getUserId(), roomId, clientMessageId, body);
		} catch (BusinessException e) {
			handleFailure(connection, echoId, e);
			return;
		} catch (RuntimeException e) {
			// 커밋 여부를 알 수 없다. 앱은 같은 clientMessageId·같은 본문으로 다시 보낸다(retryable=true).
			log.error("채팅 메시지 저장 중 오류. roomId={}", roomId, e);
			error(connection, echoId, ExceptionType.SERVER_ERROR, ExceptionType.SERVER_ERROR.getMessage());
			return;
		}

		ChatMessage message = ChatMessage.from(result.message());
		hub.send(connection, new ChatFrames.MessageAck(roomId, clientMessageId, result.replayed(), message));
		if (!result.replayed()) {
			try {
				publisher.publishMessage(roomId, new ChatFrames.MessageCreated(roomId, message));
			} catch (RuntimeException e) {
				// 이미 커밋됐다. 다른 참여자는 PONG 의 최신 ID·재연결 복구(API-076)로 받는다.
				log.warn("채팅 메시지 방송에 실패했습니다. roomId={}, messageId={}", roomId, message.id(), e);
			}
		}
	}

	/* ── PING ────────────────────────────────────────────── */

	private void ping(ChatConnection connection) {
		try {
			Long latest = chatMessageService.checkAccess(connection.getUserId(), connection.getRoomId());
			hub.send(connection, new ChatFrames.Pong(connection.getRoomId(), latest));
		} catch (BusinessException e) {
			handleFailure(connection, null, e);
		} catch (RuntimeException e) {
			log.error("채팅 PING 처리 중 오류. roomId={}", connection.getRoomId(), e);
			error(connection, null, ExceptionType.SERVER_ERROR, ExceptionType.SERVER_ERROR.getMessage());
		}
	}

	/* ── 공통 ────────────────────────────────────────────── */

	private void handleFailure(ChatConnection connection, String clientMessageId, BusinessException e) {
		if (FATAL.contains(e.getExceptionType())) {
			hub.reject(connection, connection.getRoomId(), clientMessageId, e.getExceptionType(), e.getMessage());
		} else {
			error(connection, clientMessageId, e.getExceptionType(), e.getMessage());
		}
	}

	private void error(ChatConnection connection, String clientMessageId, ExceptionType type, String message) {
		hub.send(connection, ChatFrames.Error.of(connection.getRoomId(), clientMessageId, type, message));
	}

	/** JSON 이 아니면 null. */
	private JsonNode parse(String payload) {
		try {
			return jsonMapper.readTree(payload);
		} catch (JacksonException e) {
			return null;
		}
	}

	private static String textOrNull(JsonNode value) {
		return value != null && value.isString() ? value.stringValue() : null;
	}
}
