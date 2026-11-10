package com.ssafy.yeogirok.domain.job;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 비동기 작업 하나(Redis {@code job:{jobId}}). 작업 종류마다 결과는 {@code result} 에 기능별 식별자로 담는다
 * (예: 일정 초안 {@code {"type": "SCHEDULE_DRAFT", "draftKey": "..."}}).
 *
 * @param jobId       UUID
 * @param type        작업 종류(예: {@code SCHEDULE_DRAFT}). 권한 규칙({@link JobAccessPolicy})을 고르는 데도 쓴다
 * @param requesterId 작업을 요청한 사용자
 * @param resourceId  작업이 연결된 자원(예: 여행방 ID). 권한 규칙이 현재 접근 권한을 다시 확인할 때 쓴다. 없으면 null
 * @param result      성공했을 때만 값이 있다
 * @param error       실패했을 때만 값이 있다
 * @param retryable   실패했을 때 같은 요청을 다시 보내 볼 만한지
 * @param deadline    QUEUED 면 실행을 시작해야 하는 시각(접수 + 대기 제한), RUNNING 이면 끝나야 하는 시각(시작 + 실행 제한).
 *                    넘기면 실패로 본다(대기열이 밀렸거나 서버가 처리 도중 재시작된 경우)
 */
public record Job(String jobId, String type, Long requesterId, String resourceId, JobStatus status,
	Map<String, Object> result, JobError error, boolean retryable, Instant createdAt, Instant updatedAt,
	Instant deadline) {

	/** 시간 안에 끝나지 않은 작업의 오류. */
	public static final JobError TIMED_OUT = new JobError("C014", "작업이 제한 시간 안에 끝나지 않았습니다. 다시 요청해 주세요.");

	public Job {
		Objects.requireNonNull(jobId, "jobId");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(status, "status");
		result = result == null ? null : Map.copyOf(result);
	}

	public static Job queued(String jobId, String type, Long requesterId, String resourceId, Instant now,
		Duration queueTimeout) {
		return new Job(jobId, type, requesterId, resourceId, JobStatus.QUEUED, null, null, false, now, now,
			now.plus(queueTimeout));
	}

	/** 실행을 시작한다. 기한은 이때부터 다시 센다(대기한 시간은 실행 제한 시간에 넣지 않는다). */
	public Job running(Instant now, Duration runTimeout) {
		return new Job(jobId, type, requesterId, resourceId, JobStatus.RUNNING, null, null, false, createdAt, now,
			now.plus(runTimeout));
	}

	/** 기한이 지났는지. {@link #asOf} 가 실패로 보여 주는 기준과 같다. */
	public boolean expiredAt(Instant now) {
		return deadline != null && now.isAfter(deadline);
	}

	public Job succeeded(Map<String, Object> result, Instant now) {
		return new Job(jobId, type, requesterId, resourceId, JobStatus.SUCCEEDED, result, null, false, createdAt, now,
			deadline);
	}

	public Job failed(JobError error, boolean retryable, Instant now) {
		return new Job(jobId, type, requesterId, resourceId, JobStatus.FAILED, null, error, retryable, createdAt, now,
			deadline);
	}

	/** 지금 보여 줄 상태. 끝나지 않았는데 기한이 지났으면 실패(다시 시도 가능)로 본다. 저장값은 바꾸지 않는다. */
	public Job asOf(Instant now) {
		if (!status.isFinished() && expiredAt(now)) {
			return failed(TIMED_OUT, true, updatedAt);
		}
		return this;
	}
}
