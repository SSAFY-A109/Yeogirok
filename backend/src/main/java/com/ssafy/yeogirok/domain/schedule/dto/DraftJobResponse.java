package com.ssafy.yeogirok.domain.schedule.dto;

import com.ssafy.yeogirok.domain.job.JobStatus;

/**
 * API-037·084 응답(202). 결과는 API-011 로 상태를 보고 API-038 로 조회한다.
 *
 * @param status 항상 QUEUED(명세). 같은 멱등키로 다시 보내도 같다
 */
public record DraftJobResponse(String jobId, String draftKey, JobStatus status) {

	public static DraftJobResponse queued(String jobId, String draftKey) {
		return new DraftJobResponse(jobId, draftKey, JobStatus.QUEUED);
	}
}
