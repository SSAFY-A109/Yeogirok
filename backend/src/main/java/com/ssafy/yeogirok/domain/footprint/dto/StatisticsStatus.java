package com.ssafy.yeogirok.domain.footprint.dto;

/**
 * 발자취 저장·삭제 응답의 통계 반영 상태(노션 API-041·045).
 *
 * <p>지금은 항상 {@link #PENDING} 을 준다(11/4 결정): "통계 반영이 늦을 수 있음"이라 046 을 조회 시 계산하든
 * 비동기로 집계하든 틀리지 않는다.
 */
public enum StatisticsStatus {
	PENDING,
	UNCHANGED
}
