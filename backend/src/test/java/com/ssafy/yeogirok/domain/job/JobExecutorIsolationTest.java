package com.ssafy.yeogirok.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.context.ActiveProfiles;

import com.ssafy.yeogirok.support.TestAuthConfig;

/**
 * 작업 실행기(jobExecutor)를 빈으로 두어도 스프링 부트 기본 실행기(applicationTaskExecutor)가 만들어지고, @Async(팁 게시·수정)는
 * 그 실행기에서 돈다. 기본 실행기가 없으면 @Async 는 작업마다 새 스레드를 만드는 SimpleAsyncTaskExecutor 로 돌고, 종료할 때
 * 기다리는 설정(spring.task.execution.shutdown)도 적용되지 않는다.
 *
 * <p>확인용 @Async 빈을 더해 컨텍스트가 따로 뜬다. 다른 테스트와 H2 메모리 DB 를 나눠 쓰지 않게 DB 이름을 바꾼다.
 */
@SpringBootTest(properties = "spring.datasource.url="
	+ "jdbc:h2:mem:job-executor;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@ActiveProfiles("test")
@Import({TestAuthConfig.class, JobExecutorIsolationTest.AsyncProbeConfig.class})
class JobExecutorIsolationTest {

	@Autowired
	ApplicationContext context;

	@Autowired
	AsyncProbe probe;

	@Test
	void 기본_실행기가_만들어지고_Async_는_그_실행기에서_돈다() throws Exception {
		assertThat(context.containsBean("applicationTaskExecutor")).isTrue();
		assertThat(context.containsBean(JobConfig.EXECUTOR)).isTrue();

		String thread = probe.threadName().get(5, TimeUnit.SECONDS);

		// 스프링 부트 기본 실행기의 스레드 이름 접두어는 "task-" 다(작업 실행기는 "job-").
		assertThat(thread).startsWith("task-");
	}

	@TestConfiguration
	static class AsyncProbeConfig {

		@Bean
		AsyncProbe asyncProbe() {
			return new AsyncProbe();
		}
	}

	static class AsyncProbe {

		@Async
		public CompletableFuture<String> threadName() {
			return CompletableFuture.completedFuture(Thread.currentThread().getName());
		}
	}
}
