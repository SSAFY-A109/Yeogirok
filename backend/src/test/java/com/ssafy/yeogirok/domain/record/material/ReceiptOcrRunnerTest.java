package com.ssafy.yeogirok.domain.record.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.job.service.JobRunner;
import com.ssafy.yeogirok.domain.job.service.JobStore;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;
import com.ssafy.yeogirok.support.HoldableExecutor;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.json.JsonMapper;

/**
 * 영수증 OCR 작업(명세 specs/record-api-2.md 4-1). 실제 Redis 와 #79 작업 실행기(호출 스레드에서 바로 실행), S3·OCR 은 가짜.
 */
class ReceiptOcrRunnerTest {

	static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
	static final String MATERIAL_ID = "m-1";
	static final String JOB_ID = "job-1";
	static final byte[] IMAGE = {1, 2, 3};
	static final Duration QUEUE_TIMEOUT = Duration.ofMinutes(10);
	static final JobProperties JOB_PROPERTIES = new JobProperties(Duration.ofHours(24), QUEUE_TIMEOUT,
		Duration.ofMinutes(2), 2);

	MutableClock clock;
	HoldableExecutor executor;
	ReceiptMaterialStore materialStore;
	ReceiptMaterials materials;
	JobStore jobStore;
	FileStorage fileStorage;
	ReceiptOcrClient ocrClient;
	ReceiptOcrRunner runner;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock = new MutableClock(NOW);
		executor = new HoldableExecutor();
		JsonMapper json = JsonMapper.builder().build();
		jobStore = new JobStore(TestRedis.template(), json, JOB_PROPERTIES, clock);
		materialStore = new ReceiptMaterialStore(TestRedis.template(), json,
			new IdempotencyProperties(Duration.ofHours(24), Duration.ofSeconds(30)), RecordTestConfig.RECORD_PROPERTIES,
			new RedisLock(TestRedis.template()));
		materials = new ReceiptMaterials(materialStore, jobStore, clock);
		fileStorage = mock(FileStorage.class);
		ocrClient = mock(ReceiptOcrClient.class);
		runner = runnerWith(new JobRunner(jobStore, executor, JOB_PROPERTIES, clock));
		when(fileStorage.readAll(anyString(), anyString(), anyLong())).thenReturn(IMAGE);
	}

	ReceiptOcrRunner runnerWith(JobRunner jobRunner) {
		return runnerWith(jobRunner, Duration.ofSeconds(20), Duration.ofSeconds(30));
	}

	ReceiptOcrRunner runnerWith(JobRunner jobRunner, Duration readTimeout, Duration slotWait) {
		return new ReceiptOcrRunner(jobRunner, materials, fileStorage, TestFileProperties.VALUE, ocrClient,
			new OcrProperties("http://ocr.test", Duration.ofSeconds(2), readTimeout, slotWait), clock);
	}

	ReceiptMaterial linkedMaterial(String materialId, String jobId) {
		ReceiptMaterial material = ReceiptMaterial.uploaded(100L, 7L, "receipt/" + materialId, NOW.minusSeconds(60),
			NOW.plus(Duration.ofHours(24))).linked("RECEIPT", jobId, NOW);
		materialStore.save(materialId, material);
		return material;
	}

	ReceiptMaterial linkedMaterial(String jobId) {
		return linkedMaterial(MATERIAL_ID, jobId);
	}

	ReceiptMaterial stored() {
		return materialStore.find(MATERIAL_ID).orElseThrow();
	}

	Job job(String jobId) {
		return jobStore.find(jobId).orElseThrow();
	}

	/** OCR 도중 사용자가 재시도(092)해 자료의 jobId 가 job-2 로 바뀐 상황. */
	void retriedDuringOcr() {
		materials.update(MATERIAL_ID, current -> current.ocrFailed(new ReceiptMaterial.Error("C014", "실패"))
			.retryQueued("job-2"));
	}

	@Test
	void 성공하면_제안을_저장하고_version_을_올리며_장소가_없어_보정이_필요하다() {
		when(ocrClient.recognize(IMAGE)).thenReturn(new OcrResult("해운대 커피", LocalDate.of(2026, 11, 8),
			LocalTime.of(14, 5)));

		runner.submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));

		ReceiptMaterial saved = stored();
		assertThat(saved.processStatus()).isEqualTo(ReceiptMaterial.Status.READY);
		assertThat(saved.version()).isEqualTo(2);
		assertThat(saved.reviewStatus()).isEqualTo(ReceiptMaterial.ReviewStatus.REQUIRED);
		assertThat(saved.missingFields()).containsExactly("place");
		assertThat(saved.extracted().paidAt()).isEqualTo(Instant.parse("2026-11-08T05:05:00Z"));
		assertThat(saved.extracted().placeCandidates()).isEmpty();
		assertThat(job(JOB_ID).status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(job(JOB_ID).result()).isEqualTo(
			Map.of("type", "RECEIPT_OCR", "materialId", MATERIAL_ID, "materialVersion", 2));
	}

	@Test
	void 날짜나_시각이_하나라도_없으면_paidAt_은_null() {
		when(ocrClient.recognize(IMAGE)).thenReturn(new OcrResult("가게", LocalDate.of(2026, 11, 8), null));

		runner.submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));

		assertThat(stored().extracted().paidAt()).isNull();
		assertThat(stored().missingFields()).containsExactly("paidTime", "place");
	}

	@Test
	void 원본은_영수증_버킷에서_영수증_상한_10MB_로_읽는다() {
		when(ocrClient.recognize(IMAGE)).thenReturn(new OcrResult(null, null, null));

		runner.submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));

		verify(fileStorage).readAll("receipts", "receipt/" + MATERIAL_ID, 10L * 1024 * 1024);
	}

	@Test
	void 읽을_수_없는_이미지는_M003_으로_끝나고_다시_시도_불가() {
		when(ocrClient.recognize(IMAGE)).thenThrow(new BusinessException(ExceptionType.FILE_VERIFICATION_FAILED));

		runner.submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));

		assertFailed("M003", false);
	}

	@Test
	void 너무_큰_이미지는_M001_로_끝나고_다시_시도_불가() {
		when(ocrClient.recognize(IMAGE)).thenThrow(new BusinessException(ExceptionType.FILE_TOO_LARGE));

		runner.submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));

		assertFailed("M001", false);
	}

	@Test
	void OCR_서버_오류나_시간_초과는_C014_로_끝나고_다시_시도_가능() {
		when(ocrClient.recognize(IMAGE)).thenThrow(new IllegalStateException("status=500"));

		runner.submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));

		assertFailed("C014", true);
	}

	@Test
	void 원본_보관_기한이_지났으면_M004_이고_OCR_을_부르지_않는다() {
		ReceiptMaterial material = linkedMaterial(JOB_ID);
		clock.set(material.deleteAfter());

		runner.submit(MATERIAL_ID, JOB_ID, material);

		assertFailed("M004", false);
		verify(ocrClient, never()).recognize(any());
	}

	@Test
	void 저장소_일시_장애는_다시_시도_가능() {
		when(fileStorage.readAll(anyString(), anyString(), anyLong()))
			.thenThrow(new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE));

		runner.submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));

		assertFailed("C014", true);
	}

	@Test
	void OCR_도중_jobId_가_바뀌면_늦게_성공한_작업은_자료를_덮지_않는다() {
		when(ocrClient.recognize(IMAGE)).thenAnswer(invocation -> {
			retriedDuringOcr();
			return new OcrResult("가게", LocalDate.of(2026, 11, 8), LocalTime.of(9, 0));
		});

		runner.submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));

		ReceiptMaterial saved = stored();
		assertThat(saved.jobId()).isEqualTo("job-2");
		assertThat(saved.processStatus()).isEqualTo(ReceiptMaterial.Status.UPLOADED);
		assertThat(saved.extracted()).isNull();
		assertThat(saved.version()).isEqualTo(3);
	}

	@Test
	void OCR_도중_jobId_가_바뀌면_늦게_실패한_작업도_자료를_실패로_바꾸지_않는다() {
		when(ocrClient.recognize(IMAGE)).thenAnswer(invocation -> {
			retriedDuringOcr();
			throw new IllegalStateException("status=500");
		});

		runner.submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));

		assertThat(stored().processStatus()).isEqualTo(ReceiptMaterial.Status.UPLOADED);
		assertThat(stored().error()).isNull();
		assertThat(job(JOB_ID).status()).isEqualTo(JobStatus.FAILED);
	}

	@Test
	void 지난_작업이_실행되면_자료를_건드리지_않고_끝낸다() {
		linkedMaterial("job-2");

		runner.recognize(MATERIAL_ID, JOB_ID);

		assertThat(stored().processStatus()).isEqualTo(ReceiptMaterial.Status.UPLOADED);
		verify(ocrClient, never()).recognize(any());
	}

	@Test
	void OCR_은_한_번에_한_건만_부른다() throws Exception {
		linkedMaterial(JOB_ID);
		linkedMaterial("m-2", "job-2");
		AtomicInteger running = new AtomicInteger();
		AtomicInteger maxRunning = new AtomicInteger();
		when(ocrClient.recognize(IMAGE)).thenAnswer(invocation -> {
			maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
			Thread.sleep(200);
			running.decrementAndGet();
			return new OcrResult(null, null, null);
		});

		CompletableFuture<?> first = CompletableFuture.runAsync(() -> runner.recognize(MATERIAL_ID, JOB_ID));
		CompletableFuture<?> second = CompletableFuture.runAsync(() -> runner.recognize("m-2", "job-2"));
		CompletableFuture.allOf(first, second).get(5, TimeUnit.SECONDS);

		assertThat(maxRunning.get()).isEqualTo(1);
		assertThat(stored().processStatus()).isEqualTo(ReceiptMaterial.Status.READY);
		assertThat(materialStore.find("m-2").orElseThrow().processStatus()).isEqualTo(ReceiptMaterial.Status.READY);
	}

	@Test
	void 앞_OCR_이_응답_제한_가까이_걸려도_뒤_OCR_이_차례_대기로_실패하지_않는다() throws Exception {
		// 응답 제한 200ms, 차례 대기 2s. 앞 건이 응답 제한보다 오래(300ms) 걸려도 뒤 건은 차례를 기다려 끝난다.
		ReceiptOcrRunner slow = runnerWith(new JobRunner(jobStore, executor, JOB_PROPERTIES, clock),
			Duration.ofMillis(200), Duration.ofSeconds(2));
		linkedMaterial(JOB_ID);
		linkedMaterial("m-2", "job-2");
		CountDownLatch firstStarted = new CountDownLatch(1);
		when(ocrClient.recognize(IMAGE)).thenAnswer(invocation -> {
			firstStarted.countDown();
			Thread.sleep(300);
			return new OcrResult(null, null, null);
		});

		CompletableFuture<?> first = CompletableFuture.runAsync(() -> slow.recognize(MATERIAL_ID, JOB_ID));
		firstStarted.await(5, TimeUnit.SECONDS);
		CompletableFuture<?> second = CompletableFuture.runAsync(() -> slow.recognize("m-2", "job-2"));
		CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);

		assertThat(stored().processStatus()).isEqualTo(ReceiptMaterial.Status.READY);
		assertThat(materialStore.find("m-2").orElseThrow().processStatus()).isEqualTo(ReceiptMaterial.Status.READY);
	}

	@Test
	void 실행기에_맡기지_못하면_자료를_바로_실패로_바꾼다() {
		JobRunner rejecting = new JobRunner(jobStore, task -> {
			throw new RejectedExecutionException();
		}, JOB_PROPERTIES, clock);

		runnerWith(rejecting).submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));

		assertThat(stored().processStatus()).isEqualTo(ReceiptMaterial.Status.FAILED);
		assertThat(stored().error().code()).isEqualTo("C014");
	}

	@Test
	void 대기_제한_시간이_지나_실행하지_않은_작업도_자료를_실패로_바꾼다() {
		executor.hold();
		runner.submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));
		clock.advance(QUEUE_TIMEOUT.plusSeconds(1));

		executor.runHeld();

		assertThat(stored().processStatus()).isEqualTo(ReceiptMaterial.Status.FAILED);
		assertThat(stored().error().code()).isEqualTo("C014");
		verify(ocrClient, never()).recognize(any());
	}

	@Test
	void 끊긴_작업_판정_작업이_없으면_실패로_보이고_저장값은_그대로() {
		ReceiptMaterial material = linkedMaterial(JOB_ID);

		ReceiptMaterial shown = materials.current(material);

		assertThat(shown.processStatus()).isEqualTo(ReceiptMaterial.Status.FAILED);
		assertThat(shown.error().code()).isEqualTo("C014");
		assertThat(shown.reviewStatus()).isEqualTo(ReceiptMaterial.ReviewStatus.REQUIRED);
		assertThat(shown.version()).isEqualTo(1);
		assertThat(stored().processStatus()).isEqualTo(ReceiptMaterial.Status.UPLOADED);
	}

	@Test
	void 끊긴_작업_판정_기한_안이면_진행_중_기한을_넘기면_실패() {
		executor.hold();
		runner.submit(MATERIAL_ID, JOB_ID, linkedMaterial(JOB_ID));
		assertThat(materials.current(stored()).processStatus()).isEqualTo(ReceiptMaterial.Status.UPLOADED);

		clock.advance(QUEUE_TIMEOUT.plusSeconds(1));

		assertThat(materials.current(stored()).processStatus()).isEqualTo(ReceiptMaterial.Status.FAILED);
		executor.reset();
	}

	@Test
	void 작업_안에서_자료_잠금을_못_잡으면_C014_로_끝나고_다시_시도_가능() {
		linkedMaterial(JOB_ID);
		new RedisLock(TestRedis.template()).tryAcquire("material-lock:" + MATERIAL_ID, Duration.ofSeconds(30));

		runner.submit(MATERIAL_ID, JOB_ID, stored());

		Job job = job(JOB_ID);
		assertThat(job.status()).isEqualTo(JobStatus.FAILED);
		assertThat(job.error().code()).isEqualTo("C014");
		assertThat(job.retryable()).isTrue();
		verify(ocrClient, never()).recognize(any());
	}

	@Test
	void 자료_잠금을_계속_못_잡으면_C018() {
		linkedMaterial(JOB_ID);
		new RedisLock(TestRedis.template()).tryAcquire("material-lock:" + MATERIAL_ID, Duration.ofSeconds(30));

		assertThatThrownBy(() -> materials.update(MATERIAL_ID, ReceiptMaterial::extracting))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.IDEMPOTENT_REQUEST_IN_PROGRESS));
	}

	void assertFailed(String code, boolean retryable) {
		ReceiptMaterial saved = stored();
		assertThat(saved.processStatus()).isEqualTo(ReceiptMaterial.Status.FAILED);
		assertThat(saved.version()).isEqualTo(2);
		assertThat(saved.error().code()).isEqualTo(code);
		assertThat(saved.reviewStatus()).isEqualTo(ReceiptMaterial.ReviewStatus.REQUIRED);
		Job job = job(JOB_ID);
		assertThat(job.status()).isEqualTo(JobStatus.FAILED);
		assertThat(job.error().code()).isEqualTo(code);
		assertThat(job.retryable()).isEqualTo(retryable);
	}
}
