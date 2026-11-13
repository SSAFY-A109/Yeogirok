package com.ssafy.yeogirok.domain.course.seed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 스위치({@code yeogirok.course-seed.enabled})가 켜져 있을 때만 생겨, 서버가 뜬 뒤 다른 스레드에서 적재를 한 번 돌린다(명세 6-2).
 * 어떤 오류도 밖으로 던지지 않아 서버 시작·동작에 영향을 주지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yeogirok.course-seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
class CourseSeedRunner {

	private final CourseSeedJob job;
	private final CourseSeedProperties properties;

	@Async
	@EventListener(ApplicationReadyEvent.class)
	public void onReady() {
		try {
			job.run(properties);
		} catch (RuntimeException e) {
			log.error("공식 코스 적재가 실패했습니다: {}", e.getClass().getSimpleName(), e);
		}
	}
}
