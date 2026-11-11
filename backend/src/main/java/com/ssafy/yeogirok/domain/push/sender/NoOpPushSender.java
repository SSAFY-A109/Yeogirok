package com.ssafy.yeogirok.domain.push.sender;

import java.util.Collections;
import java.util.List;

/**
 * FCM 서비스 계정 키가 없을 때(로컬·테스트). {@link #isEnabled()} 가 false 라 기능들은 받는 사람 조회부터 하지 않는다.
 * 그래도 불리면 보내지 않고 모두 실패로 돌려준다(토큰은 지우지 않는다).
 */
public class NoOpPushSender implements PushSender {

	@Override
	public List<PushResult> send(List<String> tokens, PushMessage message) {
		return Collections.nCopies(tokens.size(), PushResult.FAILED);
	}

	@Override
	public boolean isEnabled() {
		return false;
	}
}
