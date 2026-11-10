package com.ssafy.yeogirok.domain.room.dto;

/**
 * 요청자 본인의 채팅 읽음 상태(API-020·022·076). 다른 참여자의 읽은 위치는 내보내지 않는다.
 *
 * @param lastReadMessageId 마지막으로 읽은 메시지. 이력이 없으면 null
 * @param hasUnreadMessages 같은 방에 읽은 위치보다 큰 타인 메시지가 있는지(읽은 위치가 null 이면 타인 메시지 전체가 대상)
 */
public record ChatReadState(Long lastReadMessageId, boolean hasUnreadMessages) {
}
