package com.ssafy.yeogirok.domain.job.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobConfig;
import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.extern.slf4j.Slf4j;

/**
 * 비동기 작업을 만들고 실행한다. 작업 기록은 {@link JobStore} 에, 결과 자원(초안 등)은 각 기능이 따로 둔다.
 *
 * <ul>
 *   <li>QUEUED 로 먼저 저장하고 jobId 를 돌려준다. 실행은 작업 스레드에서 한다.</li>
 *   <li>실행이 끝나면 SUCCEEDED(결과) 또는 FAILED(오류)로 바꾼다. {@link BusinessException} 은 그 코드·메시지를 그대로
 *   쓰고 다시 시도할 수 없다고 본다. 그 밖의 예외는 C014 이고 다시 시도할 수 있다.</li>
 *   <li>대기열에서 대기 제한 시간 안에 시작하지 못하면 실행하지 않고 실패(C014, 다시 시도 가능)로 끝낸다. 실행 제한 시간은
 *   시작할 때부터 센다.</li>
 *   <li>서버가 처리 도중 재시작되면 기록이 QUEUED·RUNNING 에 남는다. 조회할 때 기한이 지났으면 실패로 보여 준다
 *   ({@link Job#asOf}).</li>
 * </ul>
 *
 * <p>트랜잭션 밖에서 부른다. 작업 스레드는 요청 트랜잭션이 커밋되기 전에 시작할 수 있다.
 */
@Slf4j
@Component
public class JobRunner {

	private final JobStore jobStore;
	private final Executor executor;
	private final JobProperties properties;
	private final Clock clock;

	public JobRunner(JobStore jobStore, @Qualifier(JobConfig.EXECUTOR) Executor executor, JobProperties properties,
		Clock clock) {
		this.jobStore = jobStore;
		this.executor = executor;
		this.properties = properties;
		this.clock = clock;
	}

	/** 새 jobId. 작업을 등록하기 전에 결과 자원 키를 미리 만들 때 함께 쓴다. */
	public static String newId() {
		return UUID.randomUUID().toString();
	}

	/**
	 * @param jobId       {@link #newId()} 로 만든 ID
	 * @param type        작업 종류
	 * @param requesterId 요청한 사용자
	 * @param resourceId  권한 확인에 쓸 연결 자원 ID. 없으면 null
	 * @param work        실제 처리. 성공하면 API-011 result 로 보일 값을 돌려준다
	 * @return 저장한 QUEUED 작업. 실행을 맡기지 못했으면 FAILED 작업
	 */
	public Job submit(String jobId, String type, Long requesterId, String resourceId,
		Supplier<Map<String, Object>> work) {
		return submit(jobId, type, requesterId, resourceId, work, () -> {
		});
	}

	/**
	 * @param onExpired 대기 제한 시간이 지나 {@code work} 를 실행하지 않고 실패로 끝낼 때 부른다. 작업에 딸린 상태(초안·잠금 등)를
	 *                  정리하는 데 쓴다. 작업 스레드에서 돈다
	 * @see #submit(String, String, Long, String, Supplier)
	 */
	public Job submit(String jobId, String type, Long requesterId, String resourceId,
		Supplier<Map<String, Object>> work, Runnable onExpired) {
		Job queued = Job.queued(jobId, type, requesterId, resourceId, clock.instant(), properties.queueTimeout());
		jobStore.save(queued);
		try {
			executor.execute(() -> run(queued, work, onExpired));
		} catch (RejectedExecutionException e) {
			log.warn("작업 실행을 맡기지 못했습니다. jobId={}, type={}", jobId, type);
			Job failed = queued.failed(new JobError(ExceptionType.SERVER_ERROR.getCode(),
				ExceptionType.SERVER_ERROR.getMessage()), true, clock.instant());
			jobStore.save(failed);
			return failed;
		}
		return queued;
	}

	private void run(Job queued, Supplier<Map<String, Object>> work, Runnable onExpired) {
		Instant now = clock.instant();
		if (queued.expiredAt(now)) {
			// 조회(API-011)에서 이미 실패로 보였을 수 있다. 늦게라도 실행해 성공으로 바뀌지 않게 건너뛴다.
			log.warn("대기 제한 시간이 지나 작업을 실행하지 않습니다. jobId={}, type={}", queued.jobId(), queued.type());
			jobStore.save(queued.failed(Job.TIMED_OUT, true, now));
			try {
				onExpired.run();
			} catch (RuntimeException e) {
				log.error("대기 시간을 넘긴 작업을 정리하지 못했습니다. jobId={}, type={}", queued.jobId(), queued.type(), e);
			}
			return;
		}
		Job running = queued.running(now, properties.runTimeout());
		jobStore.save(running);
		Job finished;
		try {
			finished = running.succeeded(work.get(), clock.instant());
		} catch (BusinessException e) {
			finished = running.failed(new JobError(e.getExceptionType().getCode(), e.getMessage()), false,
				clock.instant());
		} catch (RuntimeException e) {
			log.error("작업이 예상하지 못한 오류로 실패했습니다. jobId={}, type={}", queued.jobId(), queued.type(), e);
			finished = running.failed(new JobError(ExceptionType.SERVER_ERROR.getCode(),
				ExceptionType.SERVER_ERROR.getMessage()), true, clock.instant());
		}
		jobStore.save(finished);
	}
}
