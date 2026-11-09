package com.ssafy.yeogirok.domain.record;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.domain.record.service.RecordFinder;
import com.ssafy.yeogirok.domain.record.service.RecordQueryService;
import com.ssafy.yeogirok.domain.record.service.RecordVisitViews;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.support.MutableClock;

/** 여행 기록 서비스 @DataJpaTest 용 빈. */
@TestConfiguration
@Import({RecordCreateService.class, RecordQueryService.class, RecordVisitViews.class, RecordFinder.class,
	RoomFinder.class})
public class RecordTestConfig {

	/** 2026-11-09 00:30 Asia/Seoul. UTC 날짜(11/8)와 서울 날짜(11/9)가 다른 시각이다. */
	public static final Instant NOW = Instant.parse("2026-11-08T15:30:00Z");

	@Bean
	MutableClock clock() {
		return new MutableClock(NOW);
	}
}
