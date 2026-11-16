package com.ssafy.yeogirok.domain.room.event;

/**
 * 회원 탈퇴로 현재 참여자가 0명이 되어 방이 소프트 삭제됐다. 커밋 뒤 채팅이 방의 모든 연결을 끊고 메시지를 정리 대상으로
 * 등록한다. 나가기(API-087)로 삭제된 방은 {@link RoomMemberLeftEvent} 로 알린다.
 */
public record RoomDeletedEvent(Long roomId) {
}
