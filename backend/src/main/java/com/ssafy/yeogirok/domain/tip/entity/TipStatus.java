package com.ssafy.yeogirok.domain.tip.entity;

/** AR 팁 게시 상태(DB ENUM {@code tip_status}). 공개 조회는 PUBLISHED 만. */
public enum TipStatus {
	REGISTERING, PUBLISHED, FAILED, DELETED
}
