package com.ssafy.yeogirok.domain.chat.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.chat.dto.ChatReadResponse;
import com.ssafy.yeogirok.domain.chat.repository.RoomMessageRepository;
import com.ssafy.yeogirok.domain.room.dto.ChatReadState;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * API-088 본인 읽은 위치 갱신. 메시지 수신·저장 완료(ACK)·이력 조회·화면 진입만으로는 갱신하지 않고, 앱이 실제로 확인한
 * 위치를 이 API 로 보낸다. 읽음 갱신은 방·기록 version 을 바꾸지 않는다.
 *
 * <p>방 행을 잠그지 않는다(11/10 결정). 바꾸는 것은 본인 참여 행의 읽은 위치 하나뿐이라, 조건부 UPDATE 한 번
 * (현재 참여 중이고 기존 값보다 클 때만)으로 GREATEST 를 지킨다. 그래서 일정 저장·후보 변경·메시지 전송을 기다리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChatReadService {

	private final RoomMessageRepository roomMessageRepository;
	private final RoomMemberRepository roomMemberRepository;
	private final RoomFinder roomFinder;
	private final ChatReadStates chatReadStates;

	/**
	 * 더 작은 값은 기존 값을 유지하고 200 이다. 양수가 아니거나 이 방에 저장된 메시지가 아니면 C003
	 * (다른 방 메시지인지 없는 메시지인지 구분하지 않는다). 확인과 갱신 사이에 나갔으면 RO002.
	 */
	@Transactional
	public ChatReadResponse markRead(Long userId, Long roomId, long lastReadMessageId) {
		if (lastReadMessageId <= 0) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "lastReadMessageId 는 양의 정수여야 합니다.");
		}
		roomFinder.getForMember(roomId, userId);
		if (!roomMessageRepository.existsByIdAndRoomId(lastReadMessageId, roomId)) {
			throw new BusinessException(ExceptionType.NOT_ALLOWED_VALUE, "이 여행방의 메시지가 아닙니다.");
		}
		roomMemberRepository.advanceLastRead(roomId, userId, lastReadMessageId);
		RoomMember member = roomMemberRepository.findCurrent(roomId, userId)
			.orElseThrow(() -> new BusinessException(ExceptionType.ROOM_NOT_MEMBER));
		ChatReadState state = chatReadStates.of(member);
		return new ChatReadResponse(roomId, state.lastReadMessageId(), state.hasUnreadMessages());
	}
}
