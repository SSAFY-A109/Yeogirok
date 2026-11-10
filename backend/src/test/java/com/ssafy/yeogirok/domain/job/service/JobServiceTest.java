package com.ssafy.yeogirok.domain.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobAccessPolicy;
import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.job.dto.JobResponse;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.json.JsonMapper;

/** 비동기 작업 실행·저장·조회 권한(API-011). 실제 Redis, 작업은 호출한 스레드에서 바로 실행한다. */
class JobServiceTest {

	static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
	static final Duration QUEUE_TIMEOUT = Duration.ofMinutes(10);
	static final Duration RUN_TIMEOUT = Duration.ofMinutes(2);
	static final JobProperties PROPERTIES = new JobProperties(Duration.ofHours(24), QUEUE_TIMEOUT, RUN_TIMEOUT, 1);
	static final Long REQUESTER = 1L;
	static final Long OTHER = 2L;
	static final String GUARDED = "GUARDED";

	MutableClock clock;
	JobStore store;
	JobService service;
	/** 정책이 있는 종류: 자원 "open" 만 볼 수 있다. */
	JobAccessPolicy policy = new JobAccessPolicy() {
		@Override
		public String type() {
			return GUARDED;
		}

		@Override
		public void check(Long userId, Job job) {
			JobService.requireRequester(userId, job);
			if (!"open".equals(job.resourceId())) {
				throw new BusinessException(ExceptionType.JOB_RESOURCE_FORBIDDEN);
			}
		}
	};

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock = new MutableClock(NOW);
		store = new JobStore(TestRedis.template(), JsonMapper.builder().build(), PROPERTIES, clock);
		service = new JobService(store, List.of(policy), clock);
	}

	private JobRunner runner(Executor executor) {
		return new JobRunner(store, executor, PROPERTIES, clock);
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 성공한_작업은_결과를_보여_준다() {
		String jobId = JobRunner.newId();
		Job queued = runner(Runnable::run).submit(jobId, "ANY", REQUESTER, null,
			() -> Map.of("type", "ANY_RESULT", "draftKey", "k-1"));

		assertThat(queued.status()).isEqualTo(JobStatus.QUEUED);
		JobResponse response = service.get(REQUESTER, jobId);
		assertThat(response.status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(response.result()).containsEntry("type", "ANY_RESULT").containsEntry("draftKey", "k-1");
		assertThat(response.error()).isNull();
		assertThat(response.retryable()).isFalse();
	}

	@Test
	void 규칙_위반으로_실패하면_그_코드를_쓰고_다시_시도할_수_없다() {
		String jobId = JobRunner.newId();
		runner(Runnable::run).submit(jobId, "ANY", REQUESTER, null, () -> {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "조건을 만족할 수 없습니다.");
		});

		JobResponse response = service.get(REQUESTER, jobId);
		assertThat(response.status()).isEqualTo(JobStatus.FAILED);
		assertThat(response.result()).isNull();
		assertThat(response.error()).isEqualTo(new JobError("C003", "조건을 만족할 수 없습니다."));
		assertThat(response.retryable()).isFalse();
	}

	@Test
	void 예상하지_못한_오류는_C014이고_다시_시도할_수_있다() {
		String jobId = JobRunner.newId();
		runner(Runnable::run).submit(jobId, "ANY", REQUESTER, null, () -> {
			throw new IllegalStateException("내부 오류 내용은 보이지 않는다");
		});

		JobResponse response = service.get(REQUESTER, jobId);
		assertThat(response.error().code()).isEqualTo("C014");
		assertThat(response.error().message()).doesNotContain("내부 오류");
		assertThat(response.retryable()).isTrue();
	}

	@Test
	void 실행을_맡기지_못하면_바로_실패로_남는다() {
		String jobId = JobRunner.newId();
		Job job = runner(task -> {
			throw new RejectedExecutionException();
		}).submit(jobId, "ANY", REQUESTER, null, Map::of);

		assertThat(job.status()).isEqualTo(JobStatus.FAILED);
		assertThat(service.get(REQUESTER, jobId).retryable()).isTrue();
	}

	@Test
	void 대기_제한_시간_안에_시작하지_못한_작업은_실패로_보여_준다() {
		String jobId = JobRunner.newId();
		runner(task -> {
			// 실행하지 않는다(서버가 재시작된 경우).
		}).submit(jobId, "ANY", REQUESTER, null, Map::of);

		clock.advance(QUEUE_TIMEOUT.minusSeconds(1));
		assertThat(service.get(REQUESTER, jobId).status()).isEqualTo(JobStatus.QUEUED);
		clock.advance(Duration.ofSeconds(2));
		JobResponse response = service.get(REQUESTER, jobId);
		assertThat(response.status()).isEqualTo(JobStatus.FAILED);
		assertThat(response.error()).isEqualTo(Job.TIMED_OUT);
		assertThat(response.retryable()).isTrue();
	}

	@Test
	void 대기_제한_시간이_지난_뒤_차례가_오면_실행하지_않고_실패로_끝낸다() {
		String jobId = JobRunner.newId();
		List<Runnable> waiting = new ArrayList<>();
		AtomicBoolean worked = new AtomicBoolean();
		AtomicBoolean expired = new AtomicBoolean();
		runner(waiting::add).submit(jobId, "ANY", REQUESTER, null, () -> {
			worked.set(true);
			return Map.of();
		}, () -> expired.set(true));

		clock.advance(QUEUE_TIMEOUT.plusSeconds(1));
		waiting.forEach(Runnable::run);

		assertThat(worked).isFalse();
		assertThat(expired).isTrue();
		assertThat(store.find(jobId)).get().satisfies(job -> {
			assertThat(job.status()).isEqualTo(JobStatus.FAILED);
			assertThat(job.error()).isEqualTo(Job.TIMED_OUT);
			assertThat(job.retryable()).isTrue();
		});
	}

	@Test
	void 대기한_시간은_실행_제한_시간에_넣지_않는다() {
		String jobId = JobRunner.newId();
		List<Runnable> waiting = new ArrayList<>();
		runner(waiting::add).submit(jobId, "ANY", REQUESTER, null, () -> {
			// 시작은 대기 제한 직전, 실행은 실행 제한 직전까지 걸린다.
			clock.advance(RUN_TIMEOUT.minusSeconds(1));
			assertThat(service.get(REQUESTER, jobId).status()).isEqualTo(JobStatus.RUNNING);
			return Map.of("type", "ANY_RESULT");
		});

		clock.advance(QUEUE_TIMEOUT.minusSeconds(1));
		waiting.forEach(Runnable::run);

		assertThat(service.get(REQUESTER, jobId).status()).isEqualTo(JobStatus.SUCCEEDED);
	}

	@Test
	void 실행을_시작한_뒤_실행_제한_시간_안에_끝나지_않으면_실패로_보여_준다() {
		String jobId = JobRunner.newId();
		runner(Runnable::run).submit(jobId, "ANY", REQUESTER, null, () -> {
			clock.advance(RUN_TIMEOUT.plusSeconds(1));
			assertThat(service.get(REQUESTER, jobId).status()).isEqualTo(JobStatus.FAILED);
			return Map.of();
		});
	}

	@Test
	void 작업_기록은_만든_시각부터_보관_기간만큼_남는다() {
		String jobId = JobRunner.newId();
		runner(Runnable::run).submit(jobId, "ANY", REQUESTER, null, Map::of);

		Long ttlSeconds = TestRedis.template().getExpire("job:" + jobId);
		assertThat(ttlSeconds).isBetween(Duration.ofHours(24).toSeconds() - 5, Duration.ofHours(24).toSeconds());
	}

	@Test
	void 없는_작업과_UUID가_아닌_ID는_J001() {
		assertError(() -> service.get(REQUESTER, JobRunner.newId()), ExceptionType.JOB_NOT_FOUND);
		assertError(() -> service.get(REQUESTER, "not-a-uuid"), ExceptionType.JOB_NOT_FOUND);
	}

	@Test
	void 정책이_없는_종류는_요청자_본인만_본다() {
		String jobId = JobRunner.newId();
		runner(Runnable::run).submit(jobId, "ANY", REQUESTER, null, Map::of);

		assertError(() -> service.get(OTHER, jobId), ExceptionType.JOB_NOT_REQUESTER);
	}

	@Test
	void 정책이_있는_종류는_정책이_현재_자원_권한을_확인한다() {
		String open = JobRunner.newId();
		String closed = JobRunner.newId();
		runner(Runnable::run).submit(open, GUARDED, REQUESTER, "open", Map::of);
		runner(Runnable::run).submit(closed, GUARDED, REQUESTER, "closed", Map::of);

		assertThat(service.get(REQUESTER, open).status()).isEqualTo(JobStatus.SUCCEEDED);
		assertError(() -> service.get(OTHER, open), ExceptionType.JOB_NOT_REQUESTER);
		assertError(() -> service.get(REQUESTER, closed), ExceptionType.JOB_RESOURCE_FORBIDDEN);
	}
}
