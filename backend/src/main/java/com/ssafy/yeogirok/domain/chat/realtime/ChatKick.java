package com.ssafy.yeogirok.domain.chat.realtime;

import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * 연결 강제 종료 지시(Redis {@code chat:control}). 모든 서버가 받아 맞는 연결에 ERROR 를 보내고 닫는다.
 *
 * @param roomId null 이면 모든 방(회원 탈퇴)
 * @param userId null 이면 그 방의 모든 사람(방 삭제)
 * @param reason ERROR 프레임의 code. RO002 나감, RO001 방 삭제, A003 탈퇴
 */
public record ChatKick(Long roomId, Long userId, ExceptionType reason) {

	boolean matches(ChatConnection connection) {
		return connection.isAuthenticated()
			&& (roomId == null || roomId.equals(connection.getRoomId()))
			&& (userId == null || userId.equals(connection.getUserId()));
	}
}
