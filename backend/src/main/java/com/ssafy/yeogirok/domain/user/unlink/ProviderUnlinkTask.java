package com.ssafy.yeogirok.domain.user.unlink;

import com.ssafy.yeogirok.global.oauth.OAuthProvider;

/**
 * 실패한 제공자 연결 해제 하나({@link ProviderUnlinkTaskStore}). 성공하면 지운다.
 *
 * <p>탈퇴 회원 행에서는 sub 를 지우므로, 다시 시도하려면 원래 sub 를 여기에 따로 둔다.
 */
public record ProviderUnlinkTask(
	Long userId,
	OAuthProvider provider,
	String providerSubject,
	int attempts,
	String lastError
) {
}
