package com.ssafy.yeogirok.domain.recommendation.entity;

/** 추천 방식. 노션 ERD {@code recommendation_mode}. */
public enum RecommendationMode {
	/** 방에 담은 후보가 있어 동선(중심점 거리) 점수와 인기 점수를 함께 썼다. */
	PERSONALIZED,
	/** 담은 후보가 없어 지역·카테고리 안에서 인기 점수만 썼다. */
	REGIONAL_POPULAR,
	/** ERD 에만 있다. 지금은 쓰지 않는다. */
	FALLBACK
}
