package com.ssafy.yeogirok.domain.chat.repository;

import java.time.Instant;

import com.ssafy.yeogirok.domain.user.domain.UserStatus;

/** 메시지 한 건과 보낸 회원의 표시 정보. 탈퇴 익명화는 응답 DTO 가 한다. */
public record MessageView(
	Long id,
	Long senderId,
	String senderNickname,
	UserStatus senderStatus,
	String body,
	Instant createdAt,
	String clientMessageId
) {
}
