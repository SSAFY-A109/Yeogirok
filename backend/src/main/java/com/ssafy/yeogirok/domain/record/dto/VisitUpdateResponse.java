package com.ssafy.yeogirok.domain.record.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.record.entity.RecordStatus;

/** API-067 응답. actualVisits 는 수정 뒤 기록의 전체 방문(059 와 같은 구조). */
public record VisitUpdateResponse(List<ActualVisitView> actualVisits, Integer version, RecordStatus status) {
}
