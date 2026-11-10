package com.ssafy.yeogirok.domain.record.dto;

import com.ssafy.yeogirok.domain.record.entity.RecordStatus;

/** API-069 응답. status 는 저장 후 기록 상태. */
public record StoryWriteResponse(Long storyId, Long authorId, String body, RecordStatus status) {
}
