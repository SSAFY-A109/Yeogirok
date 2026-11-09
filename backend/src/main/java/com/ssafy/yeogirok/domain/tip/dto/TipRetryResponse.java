package com.ssafy.yeogirok.domain.tip.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.Operation;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus;

/**
 * API-051 응답. 재시도를 접수하면 202 {tipId, jobId, status, operation, operationStatus},
 * 실패한 작업 없이 이미 끝난 팁이면 200 {tipId, status} 라서 비어 있는 필드는 응답에서 뺀다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TipRetryResponse(Long tipId, String jobId, TipStatus status, Operation operation,
	OperationStatus operationStatus) {

	public static TipRetryResponse done(Long tipId, TipStatus status) {
		return new TipRetryResponse(tipId, null, status, null, null);
	}

	public boolean accepted() {
		return jobId != null;
	}
}
