package com.ssafy.yeogirok.domain.file.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 영수증 자료는 노션 Redis 4절 필드로 저장하고, 삭제 확인 전에는 수명을 걸지 않는다. 실제 Redis. */
class ReceiptMaterialStoreTest {

	static final JsonMapper JSON = JsonMapper.builder().build();
	static final Instant ACCEPTED = Instant.parse("2026-11-06T00:00:00Z");

	ReceiptMaterialStore store;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		store = new ReceiptMaterialStore(TestRedis.template(), JSON);
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
}
