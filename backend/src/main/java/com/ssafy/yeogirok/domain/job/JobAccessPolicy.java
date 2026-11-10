package com.ssafy.yeogirok.domain.job;

/**
 * 작업 종류별 조회 권한(API-011). 작업 종류마다 하나씩 빈으로 둔다. 정책이 없는 종류는 요청자 본인만 조회한다(J002).
 *
 * <p>명세: 요청자 전용이 기본이고, 작업이 연결된 현재 자원에 접근 권한이 없으면 J003 이다. 조회할 때마다 다시 확인한다.
 *
 * <p>TODO 기록 도메인: RECORD_GENERATION(API-064) 정책을 추가한다. 요청자와 상관없이 그 기록의 현재 참여자 모두 볼 수 있고,
 * 퇴장했거나 원본 방이 삭제됐으면 J003 이다. RECEIPT_OCR(API-062·092)은 업로더 본인만 보므로 지금 기본 규칙을 쓰되,
 * 현재 참여 권한(J003)도 확인하는 정책을 둔다.
 */
public interface JobAccessPolicy {

	/** 이 정책이 맡는 작업 종류. */
	String type();

	/** 볼 수 없으면 J002·J003 BusinessException 을 던진다. */
	void check(Long userId, Job job);
}
