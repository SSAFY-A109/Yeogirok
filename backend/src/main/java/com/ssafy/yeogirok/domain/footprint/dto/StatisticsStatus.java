package com.ssafy.yeogirok.domain.footprint.dto;

/**
 * 발자취 저장·삭제 응답의 통계 반영 상태(노션 API-041·045).
 *
 * <p>API-046 이 조회할 때마다 미삭제 발자취를 직접 세므로 저장·삭제는 응답 시점에 이미 반영돼 있다.
 * 그래서 지금은 항상 {@link #APPLIED} 를 준다(11/4 결정). 046 을 비동기 집계로 바꾸면 {@link #PENDING} 을 쓴다.
 */
public enum StatisticsStatus {
	/** 통계 반영이 늦을 수 있음(비동기 집계 전). */
	PENDING,
	/** 통계에 이미 반영됨. */
	APPLIED,
	/** 이 요청으로 통계가 바뀌지 않음. */
	UNCHANGED
}
