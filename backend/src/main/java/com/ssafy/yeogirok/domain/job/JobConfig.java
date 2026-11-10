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

	/**
	 * 대기열이 차면 새 작업은 바로 실패(다시 시도 가능)로 남는다. 종료할 때 실행 중인 작업을 잠시 기다린다.
	 *
	 * <p>{@code defaultCandidate = false}: 스프링 부트는 Executor 빈이 하나라도 있으면 기본 실행기(applicationTaskExecutor)를
	 * 만들지 않는다. 그러면 팁 게시(@Async)가 작업마다 새 스레드를 만드는 실행기로 돌고 종료 대기 설정도 빠진다. 이 빈을 기본 후보에서
	 * 빼서 기본 실행기가 그대로 만들어지게 한다({@code JobExecutorIsolationTest}). 이 실행기는 {@code @Qualifier(EXECUTOR)} 로만 받는다.
	 */
	@Bean(name = EXECUTOR, defaultCandidate = false)
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
