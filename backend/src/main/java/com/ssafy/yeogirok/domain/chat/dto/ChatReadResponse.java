package com.ssafy.yeogirok.domain.chat.dto;

/**
 * API-088 응답.
 *
 * @param lastReadMessageId 갱신 뒤 서버에 저장된 읽은 위치(더 작은 값을 보내면 기존 값)
 * @param hasUnreadMessages 응답 시점에 이 위치 뒤에 타인 메시지가 있는지
 */
public record ChatReadResponse(Long roomId, Long lastReadMessageId, boolean hasUnreadMessages) {
}
