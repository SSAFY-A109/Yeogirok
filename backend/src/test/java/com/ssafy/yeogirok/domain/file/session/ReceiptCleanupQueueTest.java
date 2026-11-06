package com.ssafy.yeogirok.domain.file.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 영수증 정리 대상 등록(score = 삭제 기한 Unix 초)과 Redis 장애 시 M012. 실제 Redis(Testcontainers). */
@Testcontainers
class ReceiptCleanupQueueTest {

	static final Instant DELETE_AFTER = Instant.parse("2026-11-07T00:00:00Z");

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

	static LettuceConnectionFactory connectionFactory;
	static StringRedisTemplate redis;

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redis = new StringRedisTemplate(connectionFactory);
	}

	@AfterAll
	static void disconnect() {
		connectionFactory.destroy();
	}

	@BeforeEach
	void flush() {
		redis.execute((RedisCallback<Void>)connection -> {
			connection.serverCommands().flushAll();
			return null;
		});
	}

	@Test
	void 버킷과_key_를_삭제_기한_점수로_등록한다() {
		new ReceiptCleanupQueue(redis).register("receipts", "tmp/abc", DELETE_AFTER);

		assertThat(redis.opsForZSet().score(ReceiptCleanupQueue.KEY, "receipts/tmp/abc"))
			.isEqualTo((double)DELETE_AFTER.getEpochSecond());
	}

	@Test
	void Redis_에_쓰지_못하면_M012() {
		LettuceConnectionFactory closed = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration(REDIS.getHost(), 1));
		closed.afterPropertiesSet();
		closed.start();
		try {
			ReceiptCleanupQueue queue = new ReceiptCleanupQueue(new StringRedisTemplate(closed));

			assertThatThrownBy(() -> queue.register("receipts", "tmp/abc", DELETE_AFTER))
				.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.FILE_STORAGE_UNAVAILABLE));
		} finally {
			closed.destroy();
		}
	}
}
