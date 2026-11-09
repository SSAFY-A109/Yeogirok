package com.ssafy.yeogirok.domain.tip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;
import com.ssafy.yeogirok.support.TestRedis;

/** 팁 사진 표시 tip-photo-use:{uploadId}. 실제 Redis. */
class TipPhotoClaimsTest {

	static final Duration RETENTION = Duration.ofHours(24);
	static final List<String> PHOTO = List.of("550e8400-e29b-41d4-a716-446655440010");
	static final String KEY = "tip-photo-use:550e8400-e29b-41d4-a716-446655440010";

	TipPhotoClaims claims;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		claims = new TipPhotoClaims(TestRedis.template(), new RedisLock(TestRedis.template()),
			new IdempotencyProperties(RETENTION, Duration.ofSeconds(30)));
	}

	@Test
	void 처음_건_팁만_쓸_수_있고_같은_팁은_다시_통과하며_멱등키_보관_기간만큼_둔다() {
		assertThat(claims.claim(1L, PHOTO)).isTrue();
		assertThat(claims.claim(1L, PHOTO)).isFalse();
		assertThatThrownBy(() -> claims.claim(2L, PHOTO)).extracting("exceptionType")
			.isEqualTo(ExceptionType.FILE_ACCESS_DENIED);
		assertThat(TestRedis.template().getExpire(KEY)).isBetween(RETENTION.toSeconds() - 5, RETENTION.toSeconds());
	}

	@Test
	void 사진이_없으면_아무것도_걸지_않는다() {
		assertThat(claims.claim(1L, List.of())).isFalse();
		assertThat(claims.claim(1L, null)).isFalse();
		assertThat(TestRedis.template().hasKey(KEY)).isFalse();
	}

	@Test
	void 저장이_실패하면_이번에_건_표시만_풀고_원래_있던_표시는_남긴다() {
		RuntimeException failure = new IllegalStateException("저장 실패");

		assertThatThrownBy(() -> claims.claimWhile(1L, PHOTO, () -> {
			throw failure;
		})).isSameAs(failure);
		assertThat(TestRedis.template().hasKey(KEY)).isFalse();

		claims.claim(1L, PHOTO);
		assertThatThrownBy(() -> claims.claimWhile(1L, PHOTO, () -> {
			throw failure;
		})).isSameAs(failure);
		assertThat(TestRedis.template().opsForValue().get(KEY)).isEqualTo("1");
	}

	@Test
	void 다른_팁의_표시는_풀지_않는다() {
		claims.claim(1L, PHOTO);

		claims.release(2L, PHOTO);

		assertThat(TestRedis.template().opsForValue().get(KEY)).isEqualTo("1");
	}
}
