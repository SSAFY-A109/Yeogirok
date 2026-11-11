package com.ssafy.yeogirok.domain.record.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.domain.job.service.JobRunner;
import com.ssafy.yeogirok.domain.job.service.JobStore;
import com.ssafy.yeogirok.domain.record.dto.MaterialLinkCommand;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.json.JsonMapper;

/**
 * API-062 를 다른 멱등키로 동시에 보내도 자료 잠금 안에서 연결 여부를 보므로 한 번만 연결된다. 실제 Redis, 기록 권한 확인은 가짜
 * (RDB 는 MaterialLinkServiceTest 가 본다). OCR 작업은 실행하지 않고 쌓아 둔다.
 */
class MaterialLinkConcurrencyTest {

	static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
	static final String UPLOAD_ID = "0a1b2c3d-0000-4000-8000-000000000001";
	static final int REQUESTS = 4;

	@Test
	void 같은_자료를_동시에_연결하면_하나만_성공하고_나머지는_RE009() throws Exception {
		TestRedis.flushAll();
		MutableClock clock = new MutableClock(NOW);
		JsonMapper json = JsonMapper.builder().build();
		IdempotencyProperties idempotency = new IdempotencyProperties(Duration.ofHours(24), Duration.ofSeconds(30));
		JobProperties jobProperties = new JobProperties(Duration.ofHours(24), Duration.ofMinutes(10),
			Duration.ofMinutes(2), 2);
		JobStore jobStore = new JobStore(TestRedis.template(), json, jobProperties, clock);
		ReceiptMaterialStore materialStore = new ReceiptMaterialStore(TestRedis.template(), json, idempotency,
			new RedisLock(TestRedis.template()));
		ReceiptMaterials materials = new ReceiptMaterials(materialStore, jobStore, clock);
		List<Runnable> queued = Collections.synchronizedList(new ArrayList<>());
		ReceiptOcrRunner runner = new ReceiptOcrRunner(new JobRunner(jobStore, queued::add, jobProperties, clock),
			materials, mock(FileStorage.class), TestFileProperties.VALUE, mock(ReceiptOcrClient.class),
			new OcrProperties("http://ocr.test", Duration.ofSeconds(2), Duration.ofSeconds(20), Duration.ofSeconds(30)), clock);
		MaterialLinkService service = new MaterialLinkService(mock(RecordFinder.class), materials, materialStore,
			new UploadSessionStore(TestRedis.template(), json, idempotency, clock), runner, clock);
		String materialId = ReceiptMaterial.idOf(UPLOAD_ID);
		materialStore.save(materialId, ReceiptMaterial.uploaded(100L, 7L, "receipt/u", NOW, NOW.plus(Duration.ofHours(24))));
		// 처음 한 번은 직렬화 준비로 잠금을 오래 쥐어 다른 요청이 재시도 한도(약 0.4초)를 넘겨 C018 이 날 수 있어 먼저 한 번 연결해 둔다.
		String warmUp = "0a1b2c3d-0000-4000-8000-000000000009";
		materialStore.save(ReceiptMaterial.idOf(warmUp), ReceiptMaterial.uploaded(100L, 7L, "receipt/w", NOW,
			NOW.plus(Duration.ofHours(24))));
		service.link(7L, 100L, new MaterialLinkCommand(MaterialLinkCommand.Type.RECEIPT, warmUp));
		queued.clear();

		ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<String>> results = new ArrayList<>();
			for (int i = 0; i < REQUESTS; i++) {
				results.add(pool.submit(() -> {
					start.await();
					try {
						return service.link(7L, 100L,
							new MaterialLinkCommand(MaterialLinkCommand.Type.RECEIPT, UPLOAD_ID)).jobId();
					} catch (BusinessException e) {
						return e.getExceptionType().getCode();
					}
				}));
			}
			start.countDown();
			List<String> outcomes = new ArrayList<>();
			for (Future<String> result : results) {
				outcomes.add(result.get(10, TimeUnit.SECONDS));
			}

			String linkedJobId = materialStore.find(materialId).orElseThrow().jobId();
			assertThat(outcomes).as("%s", outcomes).filteredOn("RE009"::equals).hasSize(REQUESTS - 1);
			assertThat(outcomes).filteredOn(linkedJobId::equals).hasSize(1);
			assertThat(queued).hasSize(1);
		} finally {
			pool.shutdownNow();
		}
	}
}
