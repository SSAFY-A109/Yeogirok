package com.ssafy.yeogirok.domain.room.event;

/**
 * API-087 나가기가 커밋된 뒤 다른 기능이 따라 처리할 일. 채팅은 이 사람의 실시간 연결을 끊고, 방이 삭제됐으면
 * 방의 모든 연결을 끊고 메시지를 정리 대상으로 등록한다.
 *
 * @param roomDeleted 마지막 현재 참여자가 나가 방이 소프트 삭제됐는지
 */
public record RoomMemberLeftEvent(Long roomId, Long userId, boolean roomDeleted) {
}
