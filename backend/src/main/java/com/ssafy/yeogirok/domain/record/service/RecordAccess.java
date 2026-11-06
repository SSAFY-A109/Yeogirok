package com.ssafy.yeogirok.domain.record.service;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 기록 참여자 확인. 기록은 방과 같은 참여자 명단을 쓴다(노션 API-087). 어떤 오류 코드로 알릴지는 부르는 쪽이 정한다.
 *
 * <p>트랜잭션을 걸지 않는다. {@link RoomFinder} 가 던진 예외를 여기서 받으면 바깥 트랜잭션이 rollback-only 가 되기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class RecordAccess {

	private final TravelRecordRepository travelRecordRepository;
	private final RoomFinder roomFinder;

	/** 기록이 있고, 그 방이 삭제되지 않았고, 요청자가 현재 참여자다. */
	public boolean isCurrentMember(Long recordId, Long userId) {
		return travelRecordRepository.findRoomIdById(recordId)
			.map(roomId -> isRoomMember(roomId, userId))
			.orElse(false);
	}

	private boolean isRoomMember(Long roomId, Long userId) {
		try {
			roomFinder.getForMember(roomId, userId);
			return true;
		} catch (BusinessException e) {
			if (e.getExceptionType() == ExceptionType.ROOM_NOT_FOUND
				|| e.getExceptionType() == ExceptionType.ROOM_NOT_MEMBER) {
				return false;
			}
			throw e;
		}
	}
}
