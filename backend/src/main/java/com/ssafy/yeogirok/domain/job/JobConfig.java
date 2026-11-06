package com.ssafy.yeogirok.domain.job;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 비동기 작업 설정과 작업 스레드. */
@Configuration
@EnableConfigurationProperties(JobProperties.class)
public class JobConfig {

	public static final String EXECUTOR = "jobExecutor";

	/** 대기열이 차면 새 작업은 바로 실패(다시 시도 가능)로 남는다. 종료할 때 실행 중인 작업을 잠시 기다린다. */
	@Bean(EXECUTOR)
	public ThreadPoolTaskExecutor jobExecutor(JobProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.threads());
		executor.setMaxPoolSize(properties.threads());
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("job-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		return executor;
	}
}
