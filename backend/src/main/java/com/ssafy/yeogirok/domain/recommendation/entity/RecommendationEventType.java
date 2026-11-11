package com.ssafy.yeogirok.domain.recommendation.entity;

/** 추천 기록 종류. 노션 ERD {@code recommendation_event_type}. ADD·REMOVE 는 API-029·032 에서 남긴다(아직 없음). */
public enum RecommendationEventType {
	REQUEST,
	ADD,
	REMOVE
}
