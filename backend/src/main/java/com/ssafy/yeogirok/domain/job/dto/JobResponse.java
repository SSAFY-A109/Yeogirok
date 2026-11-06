package com.ssafy.yeogirok.domain.job.dto;

import java.util.Map;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.job.JobStatus;

/**
 * API-011 응답.
 *
 * @param result 성공했을 때만. 작업 종류별 식별자(예: {@code {"type": "SCHEDULE_DRAFT", "draftKey": "..."}})
 * @param error  실패했을 때만
 */
public record JobResponse(String jobId, String type, JobStatus status, Map<String, Object> result, JobError error,
	boolean retryable) {

	public static JobResponse from(Job job) {
		return new JobResponse(job.jobId(), job.type(), job.status(), job.result(), job.error(), job.retryable());
	}
}
