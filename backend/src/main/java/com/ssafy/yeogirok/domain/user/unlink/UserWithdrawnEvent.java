package com.ssafy.yeogirok.domain.user.unlink;

import com.ssafy.yeogirok.global.oauth.OAuthProvider;

/**
 * 탈퇴가 커밋된 뒤 제공자 연결 해제에 쓴다. 회원 행의 sub 는 탈퇴 때 지워지므로 원래 값을 담아 둔다.
 *
 * @param googleRevokeToken 구글 회원일 때 탈퇴 요청의 serverAuthCode 로 받은 토큰. 저장하지 않고 이벤트로만 넘긴다.
 */
public record UserWithdrawnEvent(
	Long userId,
	OAuthProvider provider,
	String providerSubject,
	String googleRevokeToken
) {

	@Override
	public String toString() {
		return "UserWithdrawnEvent[userId=" + userId + ", provider=" + provider + "]";
	}
}
