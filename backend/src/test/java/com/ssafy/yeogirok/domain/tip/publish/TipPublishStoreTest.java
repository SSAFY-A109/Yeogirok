package com.ssafy.yeogirok.domain.tip.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.json.JsonMapper;

/** 팁 게시 작업 상태 저장·조회와 키 수명(멱등키 보관 기간). 실제 Redis. */
class TipPublishStoreTest {

	static final Duration RETENTION = Duration.ofHours(24);

	TipPublishStore store;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		store = new TipPublishStore(TestRedis.template(), JsonMapper.builder().build(),
			new IdempotencyProperties(RETENTION, Duration.ofSeconds(30)));
	}

	@Test
	void 저장한_상태를_그대로_읽고_멱등키_보관_기간만큼_둔다() {
		TipPublishState failed = TipPublishState.queued("job-1", "tip-photo/x").running()
			.failed("C014", "실패", true);
		store.save(7L, failed);

		assertThat(store.find(7L)).contains(failed);
		assertThat(store.find(8L)).isEmpty();
		assertThat(TestRedis.template().getExpire("tip-publish:7"))
			.isBetween(RETENTION.toSeconds() - 5, RETENTION.toSeconds());
	}
}
