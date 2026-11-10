package com.ssafy.yeogirok.domain.chat.push;

import java.util.Map;

import com.ssafy.yeogirok.domain.push.sender.PushMessage;

/** 채팅 푸시 알림 모양(노션 API-077 "FCM HTTP v1 요청의 message 예시"). */
final class ChatPushMessages {

	/** 앱이 만드는 Android 알림 채널 ID. */
	static final String CHANNEL_ID = "chat";
	static final String TYPE = "CHAT";
	static final String ELLIPSIS = "…";

	private ChatPushMessages() {
	}

	/**
	 * 제목은 여행방 이름, 본문은 "닉네임: 메시지 앞 {@code previewLength}자". 글자 수는 메시지 본문만 코드 포인트로 센다
	 * (채팅 본문 100자 기준과 같다). 넘으면 자르고 "…" 를 붙인다. tag 는 방마다 하나라 같은 방 알림은 새 알림으로 바뀐다.
	 */
	static PushMessage of(Long roomId, Long messageId, String roomTitle, String senderNickname, String body,
		int previewLength) {
		return new PushMessage(roomTitle, senderNickname + ": " + preview(body, previewLength),
			Map.of("type", TYPE, "roomId", roomId.toString(), "messageId", messageId.toString()),
			CHANNEL_ID, "chat:" + roomId);
	}

	static String preview(String body, int previewLength) {
		if (body.codePointCount(0, body.length()) <= previewLength) {
			return body;
		}
		return body.substring(0, body.offsetByCodePoints(0, previewLength)) + ELLIPSIS;
	}
}
