package com.ssafy.yeogirok.domain.record;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.FileProperties;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.record.material.MaterialQueryService;
import com.ssafy.yeogirok.domain.record.service.RecordAuthors;
import com.ssafy.yeogirok.domain.record.service.RecordCompleteService;
import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.domain.record.service.RecordPhotoFileCleaner;
import com.ssafy.yeogirok.domain.record.service.RecordQueryService;
import com.ssafy.yeogirok.domain.record.service.RecordVisitViews;
import com.ssafy.yeogirok.domain.record.service.StoryService;
import com.ssafy.yeogirok.domain.record.service.VisitCommandService;
import com.ssafy.yeogirok.domain.record.service.VisitPhotoService;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.global.geo.LegalDong;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestFileProperties;

/** 여행 기록 서비스 @DataJpaTest 용 빈. */
@TestConfiguration
@Import({RecordCreateService.class, RecordQueryService.class, RecordVisitViews.class, VisitCommandService.class,
	StoryService.class, RecordCompleteService.class, VisitPhotoService.class, RecordPhotoFileCleaner.class,
	RecordAuthors.class, RecordFinder.class, RoomFinder.class})
public class RecordTestConfig {

	/** 2026-11-09 00:30 Asia/Seoul. UTC 날짜(11/8)와 서울 날짜(11/9)가 다른 시각이다. */
	public static final Instant NOW = Instant.parse("2026-11-08T15:30:00Z");

	/** 직접 입력 장소의 기본 법정동. */
	public static final LegalDong LEGAL_DONG = new LegalDong("4215010100", "강문동");

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

	/** 059 materials. 영수증 자료(Redis)는 MaterialQueryServiceTest 가 본다. mock 은 빈 목록을 돌려준다. */
	@Bean
	MaterialQueryService materialQueryService() {
		return mock(MaterialQueryService.class);
	}
}
