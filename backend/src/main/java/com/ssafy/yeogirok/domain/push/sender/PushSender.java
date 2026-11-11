package com.ssafy.yeogirok.domain.push.sender;

import java.util.List;

/**
 * 기기 푸시 발송. 운영은 FCM({@link FcmPushSender}), 서비스 계정 키가 없으면 보내지 않는다({@link NoOpPushSender}).
 * 테스트는 가짜 구현으로 바꿔 끼운다.
 */
public interface PushSender {

	/**
	 * 같은 알림을 여러 토큰에 보낸다. 예외를 던지지 않고, 결과는 {@code tokens} 와 같은 순서·같은 개수로 돌려준다.
	 * 스레드가 인터럽트되면(종료) 남은 토큰은 보내지 않고 {@link PushResult#UNAVAILABLE} 로 채우며 인터럽트 상태를 남긴다.
	 */
	List<PushResult> send(List<String> tokens, PushMessage message);

	/** 실제로 보낼 수 있는지. false 면 부르는 쪽이 수신자 조회부터 건너뛴다. */
	default boolean isEnabled() {
		return true;
	}

	/** 종료 때 자원을 정리한다. */
	default void close() {
	}
}
