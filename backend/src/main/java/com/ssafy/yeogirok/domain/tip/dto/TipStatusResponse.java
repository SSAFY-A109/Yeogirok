package com.ssafy.yeogirok.domain.tip.dto;

import java.util.Optional;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.Operation;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.OperationStatus;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishState.Steps;

/**
 * API-050 응답. status·version 은 RDB, 나머지는 Redis 작업 상태다. 작업 상태가 없으면(만료·유실) 작업 필드는 null 이고
 * 성공으로 단정하지 않는다(노션 050). steps 는 앱이 객체로 읽어서 null 대신 값이 모두 null 인 객체를 준다.
 */
public record TipStatusResponse(
	Long tipId,
	TipStatus status,
	String jobId,
	Steps steps,
	TipPublishState.Error error,
	boolean retryable,
	Operation operation,
	OperationStatus operationStatus,
	Integer version
) {

	private static final Steps UNKNOWN_STEPS = new Steps(null, null, null);

	public static TipStatusResponse of(ArTip tip, Optional<TipPublishState> found) {
		if (found.isEmpty()) {
			return new TipStatusResponse(tip.getId(), tip.getStatus(), null, UNKNOWN_STEPS, null, false, null, null,
				tip.getVersion());
		}
		TipPublishState state = found.get();
		return new TipStatusResponse(tip.getId(), tip.getStatus(), state.jobId(), state.steps(), state.error(),
			state.retryable(), state.operation(), state.operationStatus(), tip.getVersion());
	}
}
