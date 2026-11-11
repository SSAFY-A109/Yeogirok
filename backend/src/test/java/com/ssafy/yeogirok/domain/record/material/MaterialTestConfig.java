package com.ssafy.yeogirok.domain.record.material;

import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.session.UploadSessionStore;
import com.ssafy.yeogirok.domain.job.JobConfig;
import com.ssafy.yeogirok.domain.job.service.JobRunner;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.support.HoldableExecutor;

/**
 * 영수증 자료 서비스 @DataJpaTest 용 빈. 기록 빈(실제 Redis 포함)에 #79 작업 실행기(호출 스레드에서 바로 실행)와 자료 API 를
 * 더한다. OCR 서버는 가짜({@link ReceiptOcrClient} mock), S3 는 RecordTestConfig 의 mock.
 */
@TestConfiguration
@Import({RecordTestConfig.class, JobRunner.class, UploadSessionStore.class, ReceiptOcrRunner.class,
	MaterialLinkService.class, MaterialReviewService.class, MaterialRetryService.class})
public class MaterialTestConfig {

	@Bean(JobConfig.EXECUTOR)
	HoldableExecutor jobExecutor() {
		return new HoldableExecutor();
	}

	@Bean
	OcrProperties ocrProperties() {
		return new OcrProperties("http://ocr.test", Duration.ofSeconds(2), Duration.ofSeconds(20),
			Duration.ofSeconds(30));
	}

	@Bean
	ReceiptOcrClient receiptOcrClient() {
		return mock(ReceiptOcrClient.class);
	}
}
