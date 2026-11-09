package com.ssafy.yeogirok.domain.tip.publish;

import static com.ssafy.yeogirok.domain.tip.TipFixture.NOW;
import static com.ssafy.yeogirok.domain.tip.TipFixture.northOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.SpatialReference;
import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft.PhotoChange;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.json.JsonMapper;

/** 팁 수정안 저장·조회·삭제와 수정 예약. 실제 Redis. */
class TipEditStoreTest {

	static final Duration RETENTION = Duration.ofHours(24);
	static final Duration PROCESSING = Duration.ofSeconds(30);

	TipEditStore store;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		store = new TipEditStore(TestRedis.template(), JsonMapper.builder().build(),
			new RedisLock(TestRedis.template()), new IdempotencyProperties(RETENTION, PROCESSING));
	}

	@Test
	void 수정안을_그대로_읽고_멱등키_보관_기간만큼_두며_지울_수_있다() {
		TipEditDraft draft = new TipEditDraft(1L, 3, ArTipCategory.TMI, "새 본문", PhotoChange.REPLACE, "tip-photo/x",
			new TipEditDraft.Relocation(northOf(2), "location-v1",
				new SpatialReference(SpatialReference.PROVIDER, "anchor-2", NOW.plusSeconds(3600)),
				Map.of("schemaVersion", 1)));
		store.save(7L, "job-1", draft);

		assertThat(store.find(7L, "job-1")).contains(draft);
		assertThat(store.find(7L, "job-2")).isEmpty();
		assertThat(TestRedis.template().getExpire("tip-edit:7:job-1"))
			.isBetween(RETENTION.toSeconds() - 5, RETENTION.toSeconds());
		store.delete(7L, "job-1");
		assertThat(store.find(7L, "job-1")).isEmpty();
	}

	@Test
	void 모든_수정안의_새_사진_경로를_모으고_예약_키는_건너뛴다() {
		store.save(7L, "job-1", new TipEditDraft(1L, 1, null, null, PhotoChange.REPLACE, "tip-photo/new", null));
		store.save(8L, "job-2", new TipEditDraft(1L, 1, null, "본문", PhotoChange.KEEP, null, null));
		store.reserve(7L);

		assertThat(store.imagePaths()).containsExactly("tip-photo/new");
	}

	@Test
	void 예약은_하나만_걸리고_처리_중_표시_수명만큼_두며_내가_건_예약만_푼다() {
		String token = store.reserve(7L).orElseThrow();

		assertThat(store.reserve(7L)).isEmpty();
		assertThat(TestRedis.template().getExpire("tip-edit-lock:7")).isBetween(PROCESSING.toSeconds() - 5,
			PROCESSING.toSeconds());
		store.release(7L, "other-token");
		assertThat(store.reserve(7L)).isEmpty();
		store.release(7L, token);
		assertThat(store.reserve(7L)).isPresent();
	}
}
