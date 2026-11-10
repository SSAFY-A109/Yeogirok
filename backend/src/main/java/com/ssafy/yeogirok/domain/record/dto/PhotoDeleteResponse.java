package com.ssafy.yeogirok.domain.record.dto;

import com.ssafy.yeogirok.domain.record.entity.RecordStatus;

/** API-089 응답. deleted 는 항상 true, status·version 은 삭제 후 기록 값. */
public record PhotoDeleteResponse(Long recordId, Long imageId, boolean deleted, RecordStatus status, Integer version) {
}
