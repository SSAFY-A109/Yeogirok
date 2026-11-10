package com.ssafy.yeogirok.domain.chat.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.room.dto.ChatReadState;

/**
 * API-076 응답. items 는 오래된 → 최신 순이다.
 *
 * @param hasMore                현재 조회 방향(과거 이력) 또는 고정한 복구 범위 안에 다음 페이지가 있는지
 * @param nextAfterMessageId     복구 모드에서 hasMore=true 일 때 다음 afterMessageId. 그 밖에는 null
 * @param replayThroughMessageId 복구 모드의 고정 상한. 과거 이력 모드는 null
 */
public record ChatMessagePage(
	List<ChatMessage> items,
	boolean hasMore,
	Long nextAfterMessageId,
	Long replayThroughMessageId,
	ChatReadState chatReadState
) {
}
