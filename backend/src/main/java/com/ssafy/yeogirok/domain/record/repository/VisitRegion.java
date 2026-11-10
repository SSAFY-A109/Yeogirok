package com.ssafy.yeogirok.domain.record.repository;

/** 실제 방문 하나의 기록 ID 와 장소의 읍·면·동 이름(API-058 방문 수·지역 표시용). */
public record VisitRegion(Long recordId, String regionName) {
}
