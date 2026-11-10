package com.ssafy.yeogirok.domain.record.dto;

/** API-062 응답. 재전송도 처음과 같은 모양(version 1, UPLOADED, QUEUED)에 현재 자료의 jobId 를 담는다(명세 1-5). */
public record MaterialLinkResponse(String materialId, Long recordId, String status, Integer version, String jobId,
	String ocrStatus) {

	public static MaterialLinkResponse queued(String materialId, Long recordId, String jobId) {
		return new MaterialLinkResponse(materialId, recordId, "UPLOADED", 1, jobId, "QUEUED");
	}
}
