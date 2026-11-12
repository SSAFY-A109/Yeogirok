package com.ssafy.yeogirok.domain.record;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import com.ssafy.yeogirok.domain.record.RecordProperties;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftStore;
import com.ssafy.yeogirok.domain.record.dto.PlannedSchedule;
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
import com.ssafy.yeogirok.domain.record.service.ReferencePlans;
import com.ssafy.yeogirok.domain.record.service.StoryService;
import com.ssafy.yeogirok.domain.record.service.VisitCommandService;
import com.ssafy.yeogirok.domain.record.service.VisitPhotoService;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleReader;
import com.ssafy.yeogirok.global.geo.LegalDong;
import com.ssafy.yeogirok.global.idempotency.IdempotencyProperties;
import com.ssafy.yeogirok.global.lock.RedisLock;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.core.type.TypeReference;
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
	DraftApplication.class, RecordProgressReader.class, ReferencePlans.class, ScheduleReader.class})
public class RecordTestConfig {

	/** 2026-11-09 00:30 Asia/Seoul. UTC 날짜(11/8)와 서울 날짜(11/9)가 다른 시각이다. */
	public static final Instant NOW = Instant.parse("2026-11-08T15:30:00Z");

	/** 직접 입력 장소의 기본 법정동. */
	public static final LegalDong LEGAL_DONG = new LegalDong("4215010100", "강문동");

	/** 작업 대기 제한. 실행 제한 2분을 더한 12분이 작업·초안 기한이다. */
	public static final Duration QUEUE_TIMEOUT = Duration.ofMinutes(10);

	/** application.yaml 과 같은 값(초안 24시간, 연결한 영수증 자료 30일, 사진 묶음 기준). */
	public static final RecordProperties RECORD_PROPERTIES = new RecordProperties(Duration.ofHours(24), Duration.ofDays(30),
		new RecordProperties.Draft(Duration.ofMinutes(60), 500, Duration.ofMinutes(60), 500, 150, 300));

	/** 기록에 붙일 참고 계획 사본(API-057). 하루에 계획 항목 ID 를 순서대로 넣는다. */
	public static Map<String, Object> referencePlan(Long roomId, LocalDate date, Long... itemIds) {
		List<PlannedSchedule.Item> items = new ArrayList<>();
		for (int i = 0; i < itemIds.length; i++) {
			items.add(new PlannedSchedule.Item(itemIds[i], 100L + i, i + 1, null, null, 60, false, null));
		}
		PlannedSchedule plan = new PlannedSchedule(roomId, 1,
			List.of(new PlannedSchedule.Day(date, null, null, items, List.of(), List.of())));
		return JsonMapper.builder().build().convertValue(plan, new TypeReference<Map<String, Object>>() {
		});
	}

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
	RecordProperties recordDraftProperties() {
		return RECORD_PROPERTIES;
	}
}
