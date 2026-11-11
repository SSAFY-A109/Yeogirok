package com.ssafy.yeogirok.domain.chat.push;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 같은 수신자·같은 방 푸시를 3초에 한 번만 보낸다(노션 Redis {@code push:chat:{userId}:{roomId}}). 발송 직전에
 * SET NX EX 로 구간을 시작하고, 키가 이미 있으면 이번 푸시를 생략한다. WebSocket 실시간 수신에는 적용하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPushThrottle {

	static final String KEY_PREFIX = "push:chat:";

	private final StringRedisTemplate redis;
	private final ChatPushProperties properties;

	/** 보내도 되면 true. Redis 가 실패하면 보낸다(알림을 놓치지 않는 쪽). */
	public boolean tryAcquire(Long userId, Long roomId) {
		try {
			return !Boolean.FALSE.equals(redis.opsForValue().setIfAbsent(key(userId, roomId), "1", properties.throttle()));
		} catch (RuntimeException e) {
			log.warn("채팅 푸시 발송 제한을 확인하지 못해 그대로 보냅니다. userId={}, roomId={}", userId, roomId, e);
			return true;
		}
	}

	/** 보내지 못했다. 잡아 둔 구간을 풀어 다음 메시지에서 다시 보낼 수 있게 한다. 실패해도 구간이 끝나면 풀린다. */
	public void release(Long userId, Long roomId) {
		try {
			redis.delete(key(userId, roomId));
		} catch (RuntimeException e) {
			log.warn("채팅 푸시 발송 제한을 풀지 못했습니다. userId={}, roomId={}", userId, roomId, e);
		}
	}

	static String key(Long userId, Long roomId) {
		return KEY_PREFIX + userId + ":" + roomId;
	}
}
