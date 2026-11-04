package com.ssafy.yeogirok.domain.footprint.dto;

/** 저장·삭제 응답의 통계 반영 상태. API-046 이 조회 시 집계하므로 지금은 항상 APPLIED, 비동기 집계로 바꾸면 PENDING. */
public enum StatisticsStatus {
	PENDING,
	APPLIED,
	UNCHANGED
}
