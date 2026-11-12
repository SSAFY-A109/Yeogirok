package com.ssafy.yeogirok.domain.chat.realtime;

import java.util.List;

import com.ssafy.yeogirok.domain.chat.dto.ChatMessage;
import com.ssafy.yeogirok.domain.room.event.RoomChange;
import com.ssafy.yeogirok.domain.room.event.RoomChangedEvent;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 서버 → 앱 WebSocket JSON 프레임(노션 API-077 "채팅 양방향 WebSocket 계약"). 필드 이름과 순서는 명세 예시를 따른다.
 * 값이 없는 필드도 null 로 보낸다(ERROR 의 roomId·clientMessageId 는 판별할 수 없으면 null).
 */
public final class ChatFrames {

	private ChatFrames() {
	}

	/** 인증·참여 확인과 방 수신 준비가 끝났다. 메시지 저장이나 읽음 처리가 아니다. */
	public record Ready(String type, Long roomId, Long userId, Long latestMessageId) {
		public Ready(Long roomId, Long userId, Long latestMessageId) {
			this("READY", roomId, userId, latestMessageId);
		}
	}

	/** DB 커밋 뒤 보낸 사람에게만 보내는 저장 완료. 읽음이나 상대방 수신 확인이 아니다. */
	public record MessageAck(String type, Long roomId, String clientMessageId, boolean replayed, ChatMessage message) {
		public MessageAck(Long roomId, String clientMessageId, boolean replayed, ChatMessage message) {
			this("MESSAGE_ACK", roomId, clientMessageId, replayed, message);
		}
	}

	/** 저장 완료 메시지를 방의 현재 참여자 연결에 보낸다. 보낸 사람의 연결에도 갈 수 있다. */
	public record MessageCreated(String type, Long roomId, ChatMessage message) {
		public MessageCreated(Long roomId, ChatMessage message) {
			this("MESSAGE_CREATED", roomId, message);
		}
	}

	/**
	 * 여행방 내용이 바뀌었다(커밋 뒤). 바뀐 데이터는 싣지 않으므로 앱은 changes 에 맞는 조회를 다시 한다.
	 * 바꾼 사람의 연결에도 간다. changes 는 {@link RoomChange} 선언 순서로 정렬한다.
	 */
	public record RoomUpdated(String type, Long roomId, int version, List<RoomChange> changes, Long changedBy) {
		public static RoomUpdated from(RoomChangedEvent event) {
			return new RoomUpdated("ROOM_UPDATED", event.roomId(), event.version(),
				List.copyOf(event.changes()), event.changedBy());
		}
	}

	public record Pong(String type, Long roomId, Long latestMessageId) {
		public Pong(Long roomId, Long latestMessageId) {
			this("PONG", roomId, latestMessageId);
		}
	}

	/** 앱은 code 로만 분기한다. retryable=true 면 같은 clientMessageId·같은 본문으로 다시 보낼 수 있다. */
	public record Error(String type, Long roomId, String clientMessageId, String code, String message,
		boolean retryable) {

		public static Error of(Long roomId, String clientMessageId, ExceptionType type, String message) {
			return new Error("ERROR", roomId, clientMessageId, type.getCode(), message,
				type == ExceptionType.SERVER_ERROR);
		}
	}
}
