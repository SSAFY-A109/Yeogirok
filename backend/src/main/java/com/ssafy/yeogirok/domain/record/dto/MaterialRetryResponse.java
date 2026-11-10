package com.ssafy.yeogirok.domain.record.dto;

/** API-092 응답(202). status 는 새로 맡긴 작업의 상태다. */
public record MaterialRetryResponse(String materialId, Integer version, String jobId, String status) {

	public static MaterialRetryResponse queued(String materialId, Integer version, String jobId) {
		return new MaterialRetryResponse(materialId, version, jobId, "QUEUED");
	}
}
