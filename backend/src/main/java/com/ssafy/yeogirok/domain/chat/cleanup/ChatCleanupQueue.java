package com.ssafy.yeogirok.domain.chat.cleanup;

import java.util.HashSet;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 메시지를 지울 삭제된 방(Redis Set {@code chat-cleanup:rooms}, member 방 ID). 방 삭제 커밋 직후 등록하고 유예 기간은 없다.
 * 등록이 실패하거나 Redis 가 유실돼도 {@link ChatCleanupJob} 의 DB 안전 스캔이 찾아 지운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatCleanupQueue {

	static final String KEY = "chat-cleanup:rooms";

	private final StringRedisTemplate redis;

	/** 방은 이미 삭제·커밋됐다. 실패해도 요청을 실패시키지 않는다. */
	public void register(Long roomId) {
		try {
			redis.opsForSet().add(KEY, roomId.toString());
		} catch (RuntimeException e) {
			log.warn("채팅 정리 대상을 등록하지 못했습니다. 안전 스캔이 처리합니다. roomId={}", roomId, e);
		}
	}

	/** 숫자가 아닌 값은 로그를 남기고 목록에서 뺀다(그 값 하나 때문에 정리 전체가 멈추지 않게). */
	public Set<Long> roomIds() {
		Set<String> members = redis.opsForSet().members(KEY);
		if (members == null) {
			return Set.of();
		}
		Set<Long> roomIds = new HashSet<>();
		for (String member : members) {
			try {
				roomIds.add(Long.valueOf(member));
			} catch (NumberFormatException e) {
				log.warn("채팅 정리 목록의 잘못된 값을 뺍니다. value={}", member);
				redis.opsForSet().remove(KEY, member);
			}
		}
		return roomIds;
	}

	/** 다 지운 뒤에만 뺀다. */
	public void remove(Long roomId) {
		redis.opsForSet().remove(KEY, roomId.toString());
	}
}
