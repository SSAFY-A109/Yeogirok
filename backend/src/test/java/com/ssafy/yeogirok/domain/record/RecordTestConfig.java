package com.ssafy.yeogirok.domain.record;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.domain.job.service.JobStore;
import com.ssafy.yeogirok.domain.record.draft.DraftApplication;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftProperties;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftStore;
import com.ssafy.yeogirok.domain.record.material.MaterialQueryService;
import com.ssafy.yeogirok.domain.record.material.ReceiptMaterials;
import com.ssafy.yeogirok.domain.record.service.RecordAuthors;
import com.ssafy.yeogirok.domain.record.service.RecordCompleteService;
import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.domain.record.service.RecordPhotoFileCleaner;
import com.ssafy.yeogirok.domain.record.service.RecordProgressReader;
import com.ssafy.yeogirok.domain.record.service.RecordQueryService;
import com.ssafy.yeogirok.domain.record.service.RecordVisitViews;
import com.ssafy.yeogirok.domain.record.service.StoryService;
import com.ssafy.yeogirok.domain.record.service.VisitCommandService;
import com.ssafy.yeogirok.domain.record.service.VisitPhotoService;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.global.geo.LegalDong;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.json.JsonMapper;

/**
 * 여행 기록 서비스 @DataJpaTest 용 빈. 영수증 자료·통합 초안(059 materials, 066 초안 반영)을 읽으려고 실제 Redis(Testcontainers)를
 * 쓴다. 작업 실행기·OCR 은 MaterialTestConfig 가 더한다.
 */
@TestConfiguration
@Import({RecordCreateService.class, RecordQueryService.class, RecordVisitViews.class, VisitCommandService.class,
	StoryService.class, RecordCompleteService.class, VisitPhotoService.class, RecordPhotoFileCleaner.class,
	RecordAuthors.class, RecordFinder.class, RoomFinder.class, JobStore.class, RedisLock.class,
	ReceiptMaterialStore.class, ReceiptMaterials.class, MaterialQueryService.class, RecordDraftStore.class,
	DraftApplication.class, RecordProgressReader.class})
public class RecordTestConfig {

	/** 2026-11-09 00:30 Asia/Seoul. UTC 날짜(11/8)와 서울 날짜(11/9)가 다른 시각이다. */
	public static final Instant NOW = Instant.parse("2026-11-08T15:30:00Z");

	/** 직접 입력 장소의 기본 법정동. */
	public static final LegalDong LEGAL_DONG = new LegalDong("4215010100", "강문동");

	/** 작업 대기 제한. 실행 제한 2분을 더한 12분이 작업·초안 기한이다. */
	public static final Duration QUEUE_TIMEOUT = Duration.ofMinutes(10);

	/** application.yaml 과 같은 제안값. */
	public static final RecordDraftProperties DRAFT_PROPERTIES = new RecordDraftProperties(Duration.ofHours(24),
		new RecordDraftProperties.Draft(Duration.ofMinutes(60), 1000, Duration.ofMinutes(60), 500));

	@Bean
	MutableClock clock() {
		return new MutableClock(NOW);
	}

	/** 서명 URL 대신 쓰는 접두어. 실제 서명은 FileStorageTest 가 본다. */
	public static final String SIGNED = "https://signed.test/";

	@Bean
	FileStorage fileStorage() {
		FileStorage storage = mock(FileStorage.class);
		given(storage.presignGet(any(), any(), any())).willAnswer(invocation -> SIGNED + invocation.getArgument(1));
		return storage;
	}

	@Bean
	FileProperties fileProperties() {
		return TestFileProperties.VALUE;
	}

	@Bean
	FixedLegalDongResolver legalDongResolver() {
		return new FixedLegalDongResolver(LEGAL_DONG);
	}

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

	@Bean
	RecordDraftProperties recordDraftProperties() {
		return DRAFT_PROPERTIES;
	}
}
