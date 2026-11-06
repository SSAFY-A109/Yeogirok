package com.ssafy.yeogirok.domain.file.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.TestRedis;

/** 영수증 정리 대상 등록(score = 삭제 기한 Unix 초)과 Redis 장애 시 M012. 실제 Redis. */
class ReceiptCleanupQueueTest {

	static final Instant DELETE_AFTER = Instant.parse("2026-11-07T00:00:00Z");

	@BeforeEach
	void flush() {
		TestRedis.flushAll();
	}

	@Test
	void 버킷과_key_를_삭제_기한_점수로_등록한다() {
		new ReceiptCleanupQueue(TestRedis.template()).register("receipts", "tmp/abc", DELETE_AFTER);

		assertThat(TestRedis.template().opsForZSet().score(ReceiptCleanupQueue.KEY, "receipts/tmp/abc"))
			.isEqualTo((double)DELETE_AFTER.getEpochSecond());
	}

	@Test
	void Redis_에_쓰지_못하면_M012() {
		LettuceConnectionFactory unreachable = TestRedis.unreachable();
		try {
			ReceiptCleanupQueue queue = new ReceiptCleanupQueue(new StringRedisTemplate(unreachable));

			assertThatThrownBy(() -> queue.register("receipts", "tmp/abc", DELETE_AFTER))
				.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.FILE_STORAGE_UNAVAILABLE));
		} finally {
			unreachable.destroy();
		}
	}
}
