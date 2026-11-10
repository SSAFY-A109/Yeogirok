package com.ssafy.yeogirok.domain.chat.realtime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ssafy.yeogirok.domain.chat.cleanup.ChatCleanupQueue;
import com.ssafy.yeogirok.domain.room.event.RoomMemberLeftEvent;
import com.ssafy.yeogirok.domain.user.unlink.UserWithdrawnEvent;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 권한이 사라진 연결을 커밋 직후 끊는다(11/10 결정). 모든 서버가 받도록 Redis {@code chat:control} 로 알린다.
 * 실패해도 원래 요청은 이미 커밋됐으므로 로그만 남긴다. 그때도 다음 전송·PING 에서 권한을 다시 확인해 끊는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAccessEventListener {

	private final ChatPublisher publisher;
	private final ChatCleanupQueue cleanupQueue;

	/** 나간 사람은 RO002. 방이 삭제됐으면 방의 모든 연결을 RO001 로 끊고 메시지를 정리 대상으로 등록한다. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMemberLeft(RoomMemberLeftEvent event) {
		kick(event.roomId(), event.userId(), ExceptionType.ROOM_NOT_MEMBER);
		if (event.roomDeleted()) {
			kick(event.roomId(), null, ExceptionType.ROOM_NOT_FOUND);
			cleanupQueue.register(event.roomId());
		}
	}

	/** 탈퇴한 회원의 모든 방 연결을 A003 으로 끊는다. 메시지는 남기고 보낸 사람 표시만 익명화한다(조회 때). */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onUserWithdrawn(UserWithdrawnEvent event) {
		kick(null, event.userId(), ExceptionType.ACCESS_TOKEN_INVALID);
	}

	private void kick(Long roomId, Long userId, ExceptionType reason) {
		try {
			publisher.kick(roomId, userId, reason);
		} catch (RuntimeException e) {
			log.warn("채팅 연결 종료 알림을 보내지 못했습니다. roomId={}, userId={}, reason={}", roomId, userId, reason, e);
		}
	}
}
