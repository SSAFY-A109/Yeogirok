package com.ssafy.yeogirok.domain.job;

/**
 * 작업 종류별 조회 권한(API-011). 작업 종류마다 하나씩 빈으로 둔다. 정책이 없는 종류는 요청자 본인만 조회한다(J002).
 *
 * <p>명세: 요청자 전용이 기본이고, 작업이 연결된 현재 자원에 접근 권한이 없으면 J003 이다. 조회할 때마다 다시 확인한다.
 */
public interface JobAccessPolicy {

	/** 이 정책이 맡는 작업 종류. */
	String type();

	/** 볼 수 없으면 J002·J003 BusinessException 을 던진다. */
	void check(Long userId, Job job);
}
