package com.ssafy.yeogirok.domain.job.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobAccessPolicy;
import com.ssafy.yeogirok.domain.job.dto.JobResponse;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/**
 * API-011 비동기 작업 상태 조회. 상태만 보여 주고 결과를 적용하거나 공개하지 않는다. 결과는 각 기능의 조회 API 로 본다.
 */
@Service
public class JobService {

	private final JobStore jobStore;
	private final Map<String, JobAccessPolicy> policies;
	private final Clock clock;

	public JobService(JobStore jobStore, List<JobAccessPolicy> policies, Clock clock) {
		this.jobStore = jobStore;
		this.policies = policies.stream().collect(Collectors.toMap(JobAccessPolicy::type, Function.identity()));
		this.clock = clock;
	}

	public JobResponse get(Long userId, String jobId) {
		Job job = find(jobId);
		JobAccessPolicy policy = policies.get(job.type());
		if (policy == null) {
			requireRequester(userId, job);
		} else {
			policy.check(userId, job);
		}
		return JobResponse.from(job.asOf(clock.instant()));
	}

	/** 기본 규칙: 요청자 본인만 볼 수 있다. */
	public static void requireRequester(Long userId, Job job) {
		if (!job.requesterId().equals(userId)) {
			throw new BusinessException(ExceptionType.JOB_NOT_REQUESTER);
		}
	}

	/** UUID 형식이 아니면 없는 작업과 같이 J001 이다. */
	private Job find(String jobId) {
		try {
			UUID.fromString(jobId);
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ExceptionType.JOB_NOT_FOUND);
		}
		return jobStore.find(jobId).orElseThrow(() -> new BusinessException(ExceptionType.JOB_NOT_FOUND));
	}
}
