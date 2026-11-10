package com.ssafy.yeogirok.domain.record.dto;

/** API-064 응답(202). 재전송도 같은 모양이다. */
public record DraftJobResponse(String draftKey, String jobId, String status) {

	public static DraftJobResponse queued(String draftKey, String jobId) {
		return new DraftJobResponse(draftKey, jobId, "QUEUED");
	}
}
