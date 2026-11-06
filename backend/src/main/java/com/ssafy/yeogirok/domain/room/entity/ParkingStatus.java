package com.ssafy.yeogirok.domain.room.entity;

/** 주차 가능 여부. 장소 상세(API-027)에서 채우기 전에는 UNKNOWN. */
public enum ParkingStatus {
	AVAILABLE,
	UNAVAILABLE,
	UNKNOWN
}
