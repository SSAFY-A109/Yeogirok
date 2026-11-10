package com.ssafy.yeogirok.domain.record;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.record.service.RecordCompleteService;
import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.domain.record.service.RecordQueryService;
import com.ssafy.yeogirok.domain.record.service.RecordVisitViews;
import com.ssafy.yeogirok.domain.record.service.StoryService;
import com.ssafy.yeogirok.domain.record.service.VisitCommandService;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.global.geo.LegalDong;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;
import com.ssafy.yeogirok.support.MutableClock;

/** 여행 기록 서비스 @DataJpaTest 용 빈. */
@TestConfiguration
@Import({RecordCreateService.class, RecordQueryService.class, RecordVisitViews.class, VisitCommandService.class,
	StoryService.class, RecordCompleteService.class, RecordFinder.class, RoomFinder.class})
public class RecordTestConfig {

	/** 2026-11-09 00:30 Asia/Seoul. UTC 날짜(11/8)와 서울 날짜(11/9)가 다른 시각이다. */
	public static final Instant NOW = Instant.parse("2026-11-08T15:30:00Z");

	/** 직접 입력 장소의 기본 법정동. */
	public static final LegalDong LEGAL_DONG = new LegalDong("4215010100", "강문동");

	@Bean
	MutableClock clock() {
		return new MutableClock(NOW);
	}

	@Bean
	FixedLegalDongResolver legalDongResolver() {
		return new FixedLegalDongResolver(LEGAL_DONG);
	}
}
