package com.ssafy.yeogirok.domain.record.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.record.entity.RecordStatus;

/** API-066 응답. visitIds 는 요청 items 순서다. */
public record VisitAddResponse(List<Long> visitIds, Integer version, RecordStatus status) {
}
