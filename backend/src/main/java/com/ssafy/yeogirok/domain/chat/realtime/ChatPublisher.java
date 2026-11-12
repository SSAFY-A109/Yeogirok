package com.ssafy.yeogirok.domain.chat.realtime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis Pub/Sub 발행. 방별 채널({@code chat:room:{roomId}})에는 완성된 방 프레임(MESSAGE_CREATED·ROOM_UPDATED)을 그대로 싣고,
 * 연결 강제 종료는 공통 채널({@code chat:control})에 싣는다. 받는 쪽은 {@link ChatHub}.
 *
 * <p>Pub/Sub 수신 성공을 저장·전달 보장으로 보지 않는다. 기준은 DB 이고, 빠진 메시지는 앱이 API-076 으로 복구한다.
 */
@Component
@RequiredArgsConstructor
public class ChatPublisher {

	static final String ROOM_CHANNEL_PREFIX = "chat:room:";
	static final String CONTROL_CHANNEL = "chat:control";

	private final StringRedisTemplate redis;
	private final JsonMapper jsonMapper;

	/** 그 방에 READY 로 연결된 모든 연결에 보낼 프레임. */
	public void publishMessage(Long roomId, Object roomFrame) {
		redis.convertAndSend(ROOM_CHANNEL_PREFIX + roomId, jsonMapper.writeValueAsString(roomFrame));
	}

	public void kick(Long roomId, Long userId, ExceptionType reason) {
		redis.convertAndSend(CONTROL_CHANNEL, jsonMapper.writeValueAsString(new ChatKick(roomId, userId, reason)));
	}
}
