package com.ssafy.yeogirok.domain.chat.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.chat.repository.MessageView;
import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.domain.user.domain.UserStatus;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * 노션 API-076 Message. 이력·재연결 복구·실시간(MESSAGE_CREATED)·저장 완료(MESSAGE_ACK)가 모두 이 모양을 쓴다.
 *
 * <p>보낸 회원이 탈퇴했으면 sender.id·clientMessageId 를 null 로, 이름을 "탈퇴한 사용자"로 내보낸다.
 * 메시지 id·body·createdAt 은 그대로 둔다.
 */
public record ChatMessage(
	Long id,
	Sender sender,
	String body,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE)
	Instant createdAt,
	String clientMessageId
) {

	public static ChatMessage from(MessageView view) {
		if (view.senderStatus() == UserStatus.WITHDRAWN) {
			return new ChatMessage(view.id(), new Sender(null, User.WITHDRAWN_NICKNAME), view.body(), view.createdAt(),
				null);
		}
		return new ChatMessage(view.id(), new Sender(view.senderId(), view.senderNickname()), view.body(),
			view.createdAt(), view.clientMessageId());
	}

	public record Sender(Long id, String nickname) {
	}
}
