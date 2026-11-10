package com.ssafy.yeogirok.domain.chat.realtime;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * WebSocket 연결 티켓(노션 Redis {@code ws-ticket:{ticketId}}). 값은 {@code {userId}:{roomId}}, 수명은 발급 때 정한다.
 * AUTH 에서 GETDEL 로 읽기와 지우기를 한 번에 해 같은 티켓을 두 번 쓰지 못하게 한다. 메시지 이력·읽음 상태는 담지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ChatTicketStore {

	static final String PREFIX = "ws-ticket:";
	private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private final StringRedisTemplate redis;

	public void save(String ticket, Long userId, Long roomId, Duration ttl) {
		redis.opsForValue().set(PREFIX + ticket, userId + ":" + roomId, ttl);
	}

	/** 한 번만 성공한다. 없거나 만료·이미 쓴 티켓이면 비어 있다. */
	public Optional<TicketOwner> consume(String ticket) {
		if (ticket == null || !UUID.matcher(ticket).matches()) {
			return Optional.empty();
		}
		String value = redis.opsForValue().getAndDelete(PREFIX + ticket);
		if (value == null) {
			return Optional.empty();
		}
		int colon = value.indexOf(':');
		return Optional.of(new TicketOwner(Long.parseLong(value.substring(0, colon)),
			Long.parseLong(value.substring(colon + 1))));
	}

	public record TicketOwner(Long userId, Long roomId) {
	}
}
