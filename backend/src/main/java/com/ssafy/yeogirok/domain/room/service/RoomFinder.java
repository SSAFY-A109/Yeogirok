package com.ssafy.yeogirok.domain.room.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomMemberId;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 여행방 참여자 확인. 방이 없거나 삭제됐으면 404 RO001, 현재 참여자가 아니면(참여한 적 없거나 나갔다) 403 RO002.
 * 현재 참여자의 권한은 모두 같아 이 확인만 통과하면 조회·수정·초대를 할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class RoomFinder {

	private final TravelRoomRepository travelRoomRepository;
	private final RoomMemberRepository roomMemberRepository;

	@Transactional(readOnly = true)
	public MemberRoom getForMember(Long roomId, Long userId) {
		TravelRoom room = travelRoomRepository.findByIdAndDeletedAtIsNull(roomId)
			.orElseThrow(() -> new BusinessException(ExceptionType.ROOM_NOT_FOUND));
		return new MemberRoom(room, currentMember(roomId, userId));
	}

	/** {@link #getForMember} 와 같지만 방 행을 잠근다. 호출한 트랜잭션 안에서만 쓴다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public MemberRoom getForMemberForUpdate(Long roomId, Long userId) {
		TravelRoom room = travelRoomRepository.findByIdForUpdate(roomId)
			.filter(found -> !found.isDeleted())
			.orElseThrow(() -> new BusinessException(ExceptionType.ROOM_NOT_FOUND));
		return new MemberRoom(room, currentMember(roomId, userId));
	}

	private RoomMember currentMember(Long roomId, Long userId) {
		return roomMemberRepository.findById(new RoomMemberId(roomId, userId))
			.filter(RoomMember::isCurrent)
			.orElseThrow(() -> new BusinessException(ExceptionType.ROOM_NOT_MEMBER));
	}

	/** 방과 요청자의 현재 참여 행. */
	public record MemberRoom(TravelRoom room, RoomMember member) {
	}
}
