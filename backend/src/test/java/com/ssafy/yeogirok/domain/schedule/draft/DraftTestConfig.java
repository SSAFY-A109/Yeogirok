package com.ssafy.yeogirok.domain.schedule.draft;

import java.time.Duration;
import java.util.concurrent.Executor;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ssafy.yeogirok.domain.job.JobConfig;
import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.domain.job.service.JobRunner;
import com.ssafy.yeogirok.domain.job.service.JobService;
import com.ssafy.yeogirok.domain.job.service.JobStore;
import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.schedule.ScheduleProperties;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.json.JsonMapper;

/**
 * 초안 서비스 @DataJpaTest 용 빈. 여행방 빈(RoomTestConfig)에 실제 Redis(Testcontainers)와 작업 실행기를 더한다.
 * 작업은 요청한 스레드에서 바로 실행해 create 가 끝나면 초안 계산도 끝나 있다.
 */
@TestConfiguration
@Import({RoomTestConfig.class, JobStore.class, JobRunner.class, JobService.class, ScheduleDraftStore.class,
	ScheduleDraftLock.class, ScheduleDraftService.class, ScheduleDraftJobPolicy.class})
public class DraftTestConfig {

	public static final Duration RUN_TIMEOUT = Duration.ofMinutes(2);

	@Bean
	StringRedisTemplate stringRedisTemplate() {
		return TestRedis.template();
	}

	@Bean
	JsonMapper jsonMapper() {
		return JsonMapper.builder().build();
	}

	@Bean
	JobProperties jobProperties() {
		return new JobProperties(Duration.ofHours(24), RUN_TIMEOUT, 1);
	}

	@Bean
	ScheduleProperties scheduleProperties() {
		return new ScheduleProperties(Duration.ofHours(24));
	}

	@Bean(JobConfig.EXECUTOR)
	Executor jobExecutor() {
		return Runnable::run;
	}
}
