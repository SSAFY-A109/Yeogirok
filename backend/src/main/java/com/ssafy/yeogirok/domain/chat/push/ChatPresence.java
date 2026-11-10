package com.ssafy.yeogirok.domain.chat.push;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 지금 그 방 채팅에 READY 상태로 연결된 사용자(서버 여러 대 공통, Redis). 채팅 푸시는 여기 있는 사용자에게 보내지 않는다
 * (노션 API-077: 같은 방 채팅 화면의 WebSocket 이 READY 면 그 수신자의 모든 기기로 푸시를 생략).
 *
 * <p>방마다 정렬 집합 {@code chat:online:{roomId}} 하나. 멤버는 {@code {userId}:{연결 ID}}, 점수는 만료 시각(ms)이다.
 * READY 때 넣고 PING 마다 만료 시각을 늘리며, 연결이 닫히면 지운다. 서버가 죽어 지우지 못한 멤버는 만료 시각이 지나면
 * 조회에서 빠지고 다음 쓰기 때 지워진다. 키 자체에도 만료를 걸어 아무도 없는 방 키가 남지 않게 한다.
 *
 * <p>Redis 가 실패해도 연결·전달에는 영향을 주지 않는다(로그만 남긴다). 조회가 실패하면 아무도 접속하지 않은 것으로 보고
 * 푸시를 보낸다(채팅 화면을 보는 사람도 알림을 받을 수 있지만, 알림을 놓치는 것보다 낫다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPresence {

	static final String KEY_PREFIX = "chat:online:";

	private final StringRedisTemplate redis;
	private final ChatPushProperties properties;
	private final Clock clock;

	/** READY 또는 PING. 만료 시각을 지금부터 다시 센다. */
	public void online(Long roomId, Long userId, String connectionId) {
		String key = key(roomId);
		long now = clock.millis();
		try {
			redis.opsForZSet().add(key, member(userId, connectionId), now + properties.presenceTtl().toMillis());
			redis.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, now);
			redis.expire(key, properties.presenceTtl().multipliedBy(2));
		} catch (RuntimeException e) {
			log.warn("채팅 접속 상태를 기록하지 못했습니다. roomId={}, userId={}", roomId, userId, e);
		}
	}

	/** 연결이 닫혔다. 여러 번 불려도 된다. */
	public void offline(Long roomId, Long userId, String connectionId) {
		try {
			redis.opsForZSet().remove(key(roomId), member(userId, connectionId));
		} catch (RuntimeException e) {
			log.warn("채팅 접속 상태를 지우지 못했습니다. roomId={}, userId={}", roomId, userId, e);
		}
	}

	/** 지금 그 방에 READY 연결이 하나라도 있는 사용자. 실패하면 빈 집합. */
	public Set<Long> onlineUsers(Long roomId) {
		Set<Long> users = new HashSet<>();
		try {
			Set<String> members = redis.opsForZSet().rangeByScore(key(roomId), clock.millis(), Double.POSITIVE_INFINITY);
			if (members != null) {
				for (String member : members) {
					users.add(Long.valueOf(member.substring(0, member.indexOf(':'))));
				}
			}
		} catch (RuntimeException e) {
			log.warn("채팅 접속 상태를 읽지 못해 모든 수신자에게 푸시합니다. roomId={}", roomId, e);
			return Set.of();
		}
		return users;
	}

	static String key(Long roomId) {
		return KEY_PREFIX + roomId;
	}

	private static String member(Long userId, String connectionId) {
		return userId + ":" + connectionId;
	}
}
