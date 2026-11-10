package com.ssafy.yeogirok.domain.file.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 영수증 자료는 노션 Redis 4절 필드로 저장하고, 삭제 확인 전에는 수명을 걸지 않다가 확인 뒤 24시간 둔다. 실제 Redis. */
class ReceiptMaterialStoreTest {

	static final JsonMapper JSON = JsonMapper.builder().build();
	static final Instant ACCEPTED = Instant.parse("2026-11-06T00:00:00Z");

	ReceiptMaterialStore store;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		store = new ReceiptMaterialStore(TestRedis.template(), JSON,
			new IdempotencyProperties(Duration.ofHours(24), Duration.ofSeconds(30)));
	}

	@Test
	void 확정한_영수증_자료를_저장하고_읽는다() {
		ReceiptMaterial material = ReceiptMaterial.uploaded(100L, 7L, "receipt/abc", ACCEPTED,
			ACCEPTED.plusSeconds(86_400));
		store.save("m-1", material);

		assertThat(store.find("m-1")).contains(material);
		JsonNode stored = JSON.readTree(TestRedis.template().opsForValue().get("material:m-1"));
		assertThat(stored.get("type").stringValue()).isEqualTo("RECEIPT");
		assertThat(stored.get("processStatus").stringValue()).isEqualTo("UPLOADED");
		assertThat(stored.get("deleteStatus").stringValue()).isEqualTo("PENDING");
		assertThat(stored.get("objectKey").stringValue()).isEqualTo("receipt/abc");
		assertThat(stored.get("jobId").isNull()).isTrue();
		assertThat(TestRedis.template().getExpire("material:m-1")).isEqualTo(-1L);
	}

	@Test
	void 원본을_지우면_경로를_비우고_DELETED_로_바꾼_뒤_24시간_둔다() {
		ReceiptMaterial material = ReceiptMaterial.uploaded(100L, 7L, "receipt/abc", ACCEPTED,
			ACCEPTED.plusSeconds(86_400));
		store.save("m-1", material);

		store.markOriginalDeleted("m-1");

		ReceiptMaterial deleted = store.find("m-1").orElseThrow();
		assertThat(deleted.objectKey()).isNull();
		assertThat(deleted.deleteStatus()).isEqualTo("DELETED");
		assertThat(deleted.isOriginalAvailable(ACCEPTED)).isFalse();
		assertThat(TestRedis.template().getExpire("material:m-1")).isBetween(86_395L, 86_400L);
	}

	@Test
	void 이미_없는_자료는_만들지_않는다() {
		store.markOriginalDeleted("missing");

		assertThat(TestRedis.template().hasKey("material:missing")).isFalse();
	}

	@Test
	void 바꿔_저장해도_원본_삭제_뒤_걸린_수명은_남는다() {
		store.save("m-1", ReceiptMaterial.uploaded(100L, 7L, "receipt/abc", ACCEPTED, ACCEPTED.plusSeconds(86_400)));
		store.markOriginalDeleted("m-1");

		store.replace("m-1", store.find("m-1").orElseThrow().linked("RECEIPT", "job-1", ACCEPTED));

		assertThat(store.find("m-1").orElseThrow().jobId()).isEqualTo("job-1");
		assertThat(TestRedis.template().getExpire("material:m-1")).isBetween(86_395L, 86_400L);
	}

	@Test
	void 수명이_없던_자료는_바꿔_저장해도_수명이_없다() {
		store.save("m-1", ReceiptMaterial.uploaded(100L, 7L, "receipt/abc", ACCEPTED, ACCEPTED.plusSeconds(86_400)));

		store.replace("m-1", store.find("m-1").orElseThrow().linked("RECEIPT", "job-1", ACCEPTED));

		assertThat(TestRedis.template().getExpire("material:m-1")).isEqualTo(-1L);
	}

	@Test
	void 색인은_기록과_업로더별이고_만료된_자료는_읽을_때_뺀다() {
		ReceiptMaterial material = ReceiptMaterial.uploaded(100L, 7L, "receipt/abc", ACCEPTED,
			ACCEPTED.plusSeconds(86_400));
		store.save("m-1", material);
		store.addToIndex(100L, 7L, "m-1");
		store.addToIndex(100L, 7L, "gone");
		store.addToIndex(100L, 8L, "m-1");

		assertThat(store.indexed(100L, 7L)).containsExactly(new ReceiptMaterialEntry("m-1", material));
		assertThat(TestRedis.template().opsForSet().members("material-index:100:7")).containsExactly("m-1");
		assertThat(store.indexed(101L, 7L)).isEmpty();
	}
}
