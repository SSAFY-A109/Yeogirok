package com.ssafy.yeogirok.domain.recommendation.entity;

/** 노션 ERD {@code recommendation_log_status}. 추천은 동기로 처리해 지금은 SUCCEEDED 만 남긴다. */
public enum RecommendationLogStatus {
	QUEUED,
	RUNNING,
	SUCCEEDED,
	FAILED
}
