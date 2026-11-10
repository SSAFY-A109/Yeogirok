package com.ssafy.yeogirok.domain.push.sender;

/** 토큰 하나의 발송 결과. */
public enum PushResult {
	SENT,
	/** 앱이 지워졌거나 토큰이 더는 유효하지 않다(FCM UNREGISTERED). 그 토큰을 지운다. */
	UNREGISTERED,
	/** 그 밖의 실패. 토큰은 그대로 둔다. */
	FAILED
}
