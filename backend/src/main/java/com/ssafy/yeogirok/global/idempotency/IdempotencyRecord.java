package com.ssafy.yeogirok.global.idempotency;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 멱등키의 Redis 값. 응답 전체가 아니라 요청 해시와 결과 ID만 저장한다.
 *
 * <ul>
 *   <li>처리 중: {@code {"requestHash": …, "processingToken": …}} — 수명은 처리 중 표시 수명</li>
 *   <li>처리 끝: {@code {"requestHash": …, "resultId": …}} — 수명은 보관 기간</li>
 * </ul>
 *
 * <p>{@code processingToken} 은 처리 중 표시를 건 요청만 그 표시를 지우거나 바꾸게 하는 값이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record IdempotencyRecord(String requestHash, String resultId, String processingToken) {

	static IdempotencyRecord processing(String requestHash) {
		return new IdempotencyRecord(requestHash, null, UUID.randomUUID().toString());
	}

	static IdempotencyRecord completed(String requestHash, String resultId) {
		return new IdempotencyRecord(requestHash, resultId, null);
	}
}
