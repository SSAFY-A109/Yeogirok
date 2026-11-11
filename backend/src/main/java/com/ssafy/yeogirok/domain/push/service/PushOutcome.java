package com.ssafy.yeogirok.domain.push.service;

/** 회원 한 명에게 보낸 결과({@link PushNotifier#sendToUser}). 기능별 발송 제한을 풀지 정할 때 쓴다. */
public enum PushOutcome {
	/** 한 기기 이상에 보냈다. */
	SENT,
	/** 등록된 기기가 없다. */
	NO_DEVICE,
	/** FCM 이 모든 토큰을 거절했다(잘못된 토큰·해제된 토큰). 토큰 문제라 다음 알림은 바로 보내도 된다. */
	REJECTED,
	/** FCM 에 닿지 못했거나 FCM 쪽 장애다(일부 토큰 거절이 섞여도 여기). 바로 다시 보내지 않는 편이 낫다. */
	UNAVAILABLE
}
