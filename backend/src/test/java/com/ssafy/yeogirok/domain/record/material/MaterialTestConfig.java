package com.ssafy.yeogirok.domain.record.material;

import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.job.JobConfig;
import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.domain.job.service.JobRunner;
import com.ssafy.yeogirok.domain.job.service.JobStore;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;
import com.ssafy.yeogirok.support.HoldableExecutor;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.json.JsonMapper;

/**
 * 영수증 자료 서비스 @DataJpaTest 용 빈. 기록 빈에 실제 Redis(Testcontainers)와 #79 작업 실행기(호출 스레드에서 바로 실행)를
 * 더한다. OCR 서버는 가짜({@link ReceiptOcrClient} mock), S3 는 RecordTestConfig 의 mock.
 */
@TestConfiguration
@Import({RecordTestConfig.class, JobStore.class, JobRunner.class, RedisLock.class, ReceiptMaterialStore.class,
	UploadSessionStore.class, ReceiptMaterials.class, ReceiptOcrRunner.class, MaterialLinkService.class,
	MaterialReviewService.class, MaterialRetryService.class})
public class MaterialTestConfig {

	public static final Duration QUEUE_TIMEOUT = Duration.ofMinutes(10);

	@Bean
	StringRedisTemplate stringRedisTemplate() {
		return TestRedis.template();
	}

	@Bean
	JsonMapper jsonMapper() {
		return JsonMapper.builder().build();
	}

	@Bean
	IdempotencyProperties idempotencyProperties() {
		return new IdempotencyProperties(Duration.ofHours(24), Duration.ofSeconds(30));
	}

	@Bean
	JobProperties jobProperties() {
		return new JobProperties(Duration.ofHours(24), QUEUE_TIMEOUT, Duration.ofMinutes(2), 2);
	}

	@Bean(JobConfig.EXECUTOR)
	HoldableExecutor jobExecutor() {
		return new HoldableExecutor();
	}

	@Bean
	OcrProperties ocrProperties() {
		return new OcrProperties("http://ocr.test", Duration.ofSeconds(2), Duration.ofSeconds(45));
	}

	@Bean
	ReceiptOcrClient receiptOcrClient() {
		return mock(ReceiptOcrClient.class);
	}
}
