package com.ssafy.yeogirok.domain.user.domain;

/**
 * 회원 상태. ERD 초안의 user_status 타입. 탈퇴(API-009)하면 WITHDRAWN 이 되고, 다시 ACTIVE 로 돌아오지 않는다
 * (같은 제공자 계정으로 로그인하면 새 회원으로 가입한다).
 */
public enum UserStatus {
	ACTIVE,
	WITHDRAWN
}
