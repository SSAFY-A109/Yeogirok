package com.ssafy.yeogirok.domain.push.repository;

/** 회원 한 명의 등록 기기 토큰 하나. */
public record UserDeviceToken(Long userId, String token) {

	@Override
	public String toString() {
		// 토큰은 로그에 남기지 않는다.
		return "UserDeviceToken[userId=" + userId + "]";
	}
}
