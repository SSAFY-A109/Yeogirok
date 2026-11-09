package com.ssafy.yeogirok.domain.file.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;
import com.ssafy.yeogirok.support.TestRedis;

/** uploadId 별 완료 처리 잠금. 이미 잠겨 있으면 C018, 남이 건 잠금은 풀지 않는다. 실제 Redis. */
class UploadLockTest {

	static final String UPLOAD_ID = "550e8400-e29b-41d4-a716-446655440010";

	UploadLock lock;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		lock = new UploadLock(new RedisLock(TestRedis.template()), new IdempotencyProperties(Duration.ofHours(24),
			Duration.ofSeconds(30)));
	}

	@Test
	void 잠겨_있으면_C018_이고_풀면_다시_잡을_수_있다() {
		String token = lock.acquire(UPLOAD_ID);

		assertThatThrownBy(() -> lock.acquire(UPLOAD_ID)).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.IDEMPOTENT_REQUEST_IN_PROGRESS));

		lock.release(UPLOAD_ID, token);
		assertThat(lock.acquire(UPLOAD_ID)).isNotBlank();
	}

	@Test
	void 다른_값으로는_풀지_않는다() {
		lock.acquire(UPLOAD_ID);
		lock.release(UPLOAD_ID, "someone-else");

		assertThatThrownBy(() -> lock.acquire(UPLOAD_ID)).isInstanceOf(BusinessException.class);
	}

	@Test
	void 잠금에는_처리_중_표시와_같은_수명이_걸린다() {
		lock.acquire(UPLOAD_ID);

		assertThat(TestRedis.template().getExpire("upload-lock:" + UPLOAD_ID)).isBetween(25L, 30L);
	}
}
