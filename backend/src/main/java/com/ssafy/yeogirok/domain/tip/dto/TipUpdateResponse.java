package com.ssafy.yeogirok.domain.tip.dto;

import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.Operation;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus;

/**
 * API-054 202 응답. status·version 은 아직 바뀌지 않은 현재 값이다(공개 팁은 수정 중에도 PUBLISHED).
 * @param operationStatus 처음 요청은 QUEUED. 멱등키 재전송은 그때의 작업 상태(Redis 에 없으면 null)
 */
public record TipUpdateResponse(Long tipId, String jobId, TipStatus status, Operation operation,
	OperationStatus operationStatus, Integer version) {
}
