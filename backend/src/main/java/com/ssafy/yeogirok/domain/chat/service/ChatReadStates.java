package com.ssafy.yeogirok.domain.chat.service;

import java.util.Collection;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.chat.repository.RoomMessageRepository;
import com.ssafy.yeogirok.domain.room.dto.ChatReadState;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;

import lombok.RequiredArgsConstructor;

/**
 * 요청자 본인의 채팅 읽음 상태와 안 읽음 점. 방 목록·상세(API-020·022), 이력(076), 읽음 갱신(088)이 모두 이것을 쓴다.
 * 안 읽음 점은 같은 방에서 읽은 위치보다 큰 타인 메시지가 있는지로 정한다(읽은 위치가 null 이면 타인 메시지 전체).
 * ID 차이를 개수로 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ChatReadStates {

	private final RoomMessageRepository roomMessageRepository;

	public ChatReadState of(RoomMember member) {
		Long lastRead = member.getLastReadMessageId();
		return new ChatReadState(lastRead, roomMessageRepository.existsUnread(member.getRoomId(), member.getUserId(),
			lastRead == null ? 0L : lastRead));
	}

	/** 주어진 방 중 요청자에게 안 읽은 타인 메시지가 있는 방. */
	public Set<Long> unreadRoomIds(Long userId, Collection<Long> roomIds) {
		return Set.copyOf(roomMessageRepository.findRoomIdsWithUnread(userId, roomIds));
	}
}
