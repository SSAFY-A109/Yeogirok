package com.ssafy.yeogirok.domain.file.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.file.FilePurpose;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.json.JsonMapper;

/** 업로드 세션 저장·조회와 키 수명(최초 발급 + 멱등키 보관 기간). 실제 Redis. */
class UploadSessionStoreTest {

	static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
	static final Duration RETENTION = Duration.ofHours(24);

	MutableClock clock;
	UploadSessionStore store;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock = new MutableClock(NOW);
		store = new UploadSessionStore(TestRedis.template(), JsonMapper.builder().build(),
			new IdempotencyProperties(RETENTION, Duration.ofSeconds(30)), clock);
	}

	static UploadSession session(Instant acceptedAt) {
		return UploadSession.start("550e8400-e29b-41d4-a716-446655440010", 7L, FilePurpose.RECEIPT, 100L,
			"receipts", "image/png", 1024, acceptedAt, acceptedAt.plus(Duration.ofMinutes(10)));
	}

	@Test
	void 저장한_세션을_그대로_읽는다() {
		UploadSession saved = session(NOW);
		store.save(saved);

		assertThat(store.find(saved.uploadId())).contains(saved);
		assertThat(saved.tmpKey()).isEqualTo("tmp/550e8400-e29b-41d4-a716-446655440010");
		assertThat(saved.status()).isEqualTo(UploadStatus.UPLOADING);
	}

	@Test
	void 키는_최초_발급부터_보관_기간만큼_남고_갱신해도_늘지_않는다() {
		UploadSession saved = session(NOW);
		store.save(saved);
		clock.advance(Duration.ofHours(10));
		store.save(saved);

		Long ttlSeconds = TestRedis.template().getExpire("upload:" + saved.uploadId());
		assertThat(ttlSeconds).isBetween(RETENTION.minusHours(10).toSeconds() - 5, RETENTION.minusHours(10).toSeconds());
	}

	@Test
	void 기한이_지난_세션은_저장하지_않는다() {
		store.save(session(NOW.minus(RETENTION)));

		assertThat(store.find("550e8400-e29b-41d4-a716-446655440010")).isEmpty();
	}

	@Test
	void 없는_세션은_비어_있다() {
		assertThat(store.find("missing")).isEmpty();
	}

	@Test
	void 만료는_uploadExpiresAt_으로_판정한다() {
		UploadSession saved = session(NOW);

		assertThat(saved.isExpired(NOW.plus(Duration.ofMinutes(10)).minusMillis(1))).isFalse();
		assertThat(saved.isExpired(NOW.plus(Duration.ofMinutes(10)))).isTrue();
	}
}
