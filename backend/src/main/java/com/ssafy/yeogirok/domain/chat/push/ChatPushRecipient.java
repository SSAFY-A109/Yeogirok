package com.ssafy.yeogirok.domain.chat.push;

/**
 * 채팅 푸시를 받을 회원 한 명.
 *
 * @param roomTitle 알림 제목(여행방 이름)
 */
public record ChatPushRecipient(Long userId, String roomTitle) {
}
