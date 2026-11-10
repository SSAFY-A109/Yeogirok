package com.ssafy.yeogirok.domain.chat.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;

/** 접속 상태(chat:online)와 발송 제한(push:chat)을 실제 Redis 로 본다. */
class ChatPresenceTest {

	private static final ChatPushProperties PROPERTIES = new ChatPushProperties(Duration.ofSeconds(90),
		Duration.ofSeconds(10), 40, 1, 10);

	MutableClock clock;
	ChatPresence presence;
	ChatPushThrottle throttle;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock = new MutableClock(Instant.parse("2026-11-10T00:00:00Z"));
		presence = new ChatPresence(TestRedis.template(), PROPERTIES, clock);
		throttle = new ChatPushThrottle(TestRedis.template(), PROPERTIES);
	}

	@Test
	void READY_로_기록한_사용자만_접속_중이고_연결이_닫히면_빠진다() {
		presence.online(1L, 10L, "conn-a");
		presence.online(1L, 20L, "conn-b");
		presence.online(2L, 30L, "conn-c");

		assertThat(presence.onlineUsers(1L)).containsExactlyInAnyOrder(10L, 20L);

		presence.offline(1L, 10L, "conn-a");
		assertThat(presence.onlineUsers(1L)).containsExactly(20L);
		assertThat(presence.onlineUsers(2L)).containsExactly(30L);
	}

	@Test
	void 한_사용자가_두_연결이면_하나가_닫혀도_접속_중이다() {
		presence.online(1L, 10L, "phone");
		presence.online(1L, 10L, "tablet");

		presence.offline(1L, 10L, "phone");

		assertThat(presence.onlineUsers(1L)).containsExactly(10L);
	}

	@Test
	void 만료_시각이_지난_기록은_접속으로_보지_않고_PING_으로_늘린다() {
		presence.online(1L, 10L, "stale");
		presence.online(1L, 20L, "alive");

		clock.advance(Duration.ofSeconds(60));
		presence.online(1L, 20L, "alive"); // PING
		clock.advance(Duration.ofSeconds(31));

		assertThat(presence.onlineUsers(1L)).containsExactly(20L);
		// 다음 쓰기 때 지난 기록을 지운다.
		presence.online(1L, 20L, "alive");
		assertThat(TestRedis.template().opsForZSet().size(ChatPresence.key(1L))).isEqualTo(1);
	}

	@Test
	void 방_키에도_만료를_건다() {
		presence.online(1L, 10L, "conn");

		assertThat(TestRedis.template().getExpire(ChatPresence.key(1L))).isBetween(170L, 180L);
	}

	@Test
	void Redis_가_실패하면_아무도_접속하지_않은_것으로_보고_예외를_던지지_않는다() {
		LettuceConnectionFactory unreachable = TestRedis.unreachable();
		try {
			StringRedisTemplate broken = new StringRedisTemplate(unreachable);
			ChatPresence brokenPresence = new ChatPresence(broken, PROPERTIES, clock);

			brokenPresence.online(1L, 10L, "conn");
			brokenPresence.offline(1L, 10L, "conn");
			assertThat(brokenPresence.onlineUsers(1L)).isEmpty();
			// 발송 제한도 확인하지 못하면 보낸다.
			assertThat(new ChatPushThrottle(broken, PROPERTIES).tryAcquire(10L, 1L)).isTrue();
		} finally {
			unreachable.destroy();
		}
	}

	@Test
	void 같은_수신자_같은_방은_제한_시간_안에_한_번만_보낸다() {
		assertThat(throttle.tryAcquire(10L, 1L)).isTrue();
		assertThat(throttle.tryAcquire(10L, 1L)).isFalse();
		// 다른 방·다른 수신자는 따로 센다.
		assertThat(throttle.tryAcquire(10L, 2L)).isTrue();
		assertThat(throttle.tryAcquire(20L, 1L)).isTrue();
		assertThat(TestRedis.template().getExpire(ChatPushThrottle.key(10L, 1L))).isBetween(9L, 10L);
	}

	@Test
	void 제한을_풀면_바로_다시_보낼_수_있다() {
		assertThat(throttle.tryAcquire(10L, 1L)).isTrue();
		throttle.release(10L, 1L);
		assertThat(throttle.tryAcquire(10L, 1L)).isTrue();
		// 없는 구간을 풀어도 된다.
		throttle.release(30L, 1L);
	}
}
