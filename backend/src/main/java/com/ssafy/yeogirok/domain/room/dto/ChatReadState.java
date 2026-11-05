package com.ssafy.yeogirok.domain.room.dto;

/**
 * 요청자 본인의 채팅 읽음 상태. 채팅(room_messages)을 만들기 전이라 hasUnreadMessages 는 항상 false 다.
 *
 * @param lastReadMessageId 마지막으로 읽은 메시지. 이력이 없으면 null
 */
public record ChatReadState(Long lastReadMessageId, boolean hasUnreadMessages) {

	public static ChatReadState of(Long lastReadMessageId) {
		return new ChatReadState(lastReadMessageId, false);
	}
}
