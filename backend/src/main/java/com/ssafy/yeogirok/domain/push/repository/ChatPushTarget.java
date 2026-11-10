package com.ssafy.yeogirok.domain.push.repository;

/**
 * 채팅 푸시를 받을 기기 하나.
 *
 * @param userId    토큰 주인. UNREGISTERED 때 이 주인과 같을 때만 지운다
 * @param roomTitle 알림 제목(여행방 이름)
 */
public record ChatPushTarget(Long userId, String token, String roomTitle) {

	@Override
	public String toString() {
		// 토큰은 로그에 남기지 않는다.
		return "ChatPushTarget[userId=" + userId + "]";
	}
}
