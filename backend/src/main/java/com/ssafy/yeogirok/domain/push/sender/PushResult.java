package com.ssafy.yeogirok.domain.push.sender;

/** 토큰 하나의 발송 결과. */
public enum PushResult {
	SENT,
	/** 앱이 지워졌거나 토큰이 더는 유효하지 않다(FCM UNREGISTERED). 그 토큰을 지운다. */
	UNREGISTERED,
	/** FCM 이 이 토큰의 요청을 받지 않았다(잘못된 값 등). 토큰은 그대로 둔다. */
	FAILED,
	/**
	 * FCM 에 닿지 못했거나 FCM 쪽 장애다(네트워크, 시간 초과, UNAVAILABLE·INTERNAL·QUOTA_EXCEEDED, 종료로 멈춤).
	 * 토큰은 그대로 두고, 곧바로 다시 보내지 않는다(장애 중 재시도가 쌓이지 않게).
	 */
	UNAVAILABLE
}
