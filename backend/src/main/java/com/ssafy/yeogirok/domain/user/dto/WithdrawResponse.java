package com.ssafy.yeogirok.domain.user.dto;

/** API-009 응답 data. 후속 데이터 정리는 서버 내부 작업이라 항상 QUEUED 다(앱이 작업 상태를 조회하지 않는다). */
public record WithdrawResponse(
	boolean withdrawn,
	String dataCleanupStatus
) {

	public static WithdrawResponse queued() {
		return new WithdrawResponse(true, "QUEUED");
	}
}
