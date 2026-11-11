package com.ssafy.yeogirok.domain.push.sender;

import java.util.Map;

/**
 * 기기에 보낼 알림 하나(받는 토큰과 별개).
 *
 * @param data      알림을 눌렀을 때 앱이 읽는 값. FCM 규칙대로 값은 모두 문자열이다
 * @param channelId Android 알림 채널(앱이 만든 채널 ID)
 * @param tag       Android 알림 tag. 같은 tag 의 알림은 새 알림이 이전 알림을 바꾼다(예: 채팅은 방마다 하나만 보이게). 없으면 null
 */
public record PushMessage(String title, String body, Map<String, String> data, String channelId, String tag) {

	public PushMessage {
		data = Map.copyOf(data);
	}
}
