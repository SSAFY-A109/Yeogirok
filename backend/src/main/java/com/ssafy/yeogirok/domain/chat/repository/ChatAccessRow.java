package com.ssafy.yeogirok.domain.chat.repository;

import java.time.Instant;

import com.ssafy.yeogirok.domain.user.domain.UserStatus;

/**
 * WebSocket AUTH·PING 권한 확인용 한 줄(방·참여 행·회원을 한 번에 읽는다).
 *
 * @param roomDeletedAt   null 이면 사용 중인 방
 * @param leftAt          null 이면 현재 참여 중
 * @param latestMessageId 방의 저장 완료 메시지 중 최신 ID. 없으면 null
 */
public record ChatAccessRow(Instant roomDeletedAt, Instant leftAt, UserStatus userStatus, Long latestMessageId) {
}
