package com.ssafy.yeogirok.domain.course;

import com.ssafy.yeogirok.global.oauth.OAuthProvider;

/** 공식 코스(투어API 적재) 시스템 계정의 식별 키. 적재(SeedOwner)가 만들고 메인 추천(API-015)이 사용자 코스와 구분할 때 쓴다. */
public final class OfficialAccount {

	public static final OAuthProvider PROVIDER = OAuthProvider.KAKAO;
	/** 카카오 로그인은 숫자 회원번호(sub)로 찾으므로 이 값으로는 로그인할 수 없다. */
	public static final String SUBJECT = "system:tourapi";

	private OfficialAccount() {
	}
}
