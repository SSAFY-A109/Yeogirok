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

	@Test
	void 최신_작업이_같은_jobId_일_때만_저장한다() {
		TipPublishState queued = TipPublishState.queuedUpdate("job-2", false, false);
		store.save(7L, queued);

		assertThat(store.saveIfCurrent(7L, TipPublishState.queuedUpdate("job-1", false, false).running())).isFalse();
		assertThat(store.find(7L)).contains(queued);
		assertThat(store.saveIfCurrent(8L, queued.running())).isFalse();
		assertThat(store.find(8L)).isEmpty();

		TipPublishState running = queued.running();
		assertThat(store.saveIfCurrent(7L, running)).isTrue();
		assertThat(store.find(7L)).contains(running);
		assertThat(TestRedis.template().getExpire("tip-publish:7"))
			.isBetween(RETENTION.toSeconds() - 5, RETENTION.toSeconds());
	}

	@Test
	void 모든_작업_상태의_사진_경로를_모은다() {
		store.save(7L, TipPublishState.queued("job-1", "tip-photo/a"));
		store.save(8L, TipPublishState.queued("job-2", null));
		store.save(9L, TipPublishState.queuedUpdate("job-3", true, false));
		TestRedis.template().opsForValue().set("tip-edit-lock:7", "token");

		assertThat(store.imagePaths()).containsExactly("tip-photo/a");
	}
}
