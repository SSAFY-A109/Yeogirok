package com.ssafy.yeogirok.domain.record.service;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomMemberId;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.repository.RoomMemberRepository;
import com.ssafy.yeogirok.domain.room.repository.TravelRoomRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 기록 권한 확인(명세 {@code specs/record-api.md} 1-1). 기록은 방과 같은 참여자 명단을 쓴다.
 * <ul>
 *   <li>조회: 기록이 없거나 방이 삭제됐으면 RE001, 현재 참여자가 아니면 RE002</li>
 *   <li>쓰기: 기록이 없으면 RE001, 방이 삭제됐거나 현재 참여자가 아니면 RE003. 기록 행을 잠근 뒤에 확인한다</li>
 * </ul>
 * {@code RoomFinder} 를 부르지 않는다. 그 예외를 받아 코드를 바꾸면 바깥 트랜잭션이 rollback-only 가 되기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class RecordFinder {

	private final TravelRecordRepository travelRecordRepository;
	private final TravelRoomRepository travelRoomRepository;
	private final RoomMemberRepository roomMemberRepository;

	@Transactional(readOnly = true)
	public RecordRoom getReadable(Long recordId, Long userId) {
		TravelRecord record = travelRecordRepository.findById(recordId)
			.orElseThrow(() -> new BusinessException(ExceptionType.RECORD_NOT_FOUND));
		TravelRoom room = liveRoom(record)
			.orElseThrow(() -> new BusinessException(ExceptionType.RECORD_NOT_FOUND));
		if (!isCurrentMember(room.getId(), userId)) {
			throw new BusinessException(ExceptionType.RECORD_NOT_MEMBER);
		}
		return new RecordRoom(record, room);
	}

	/**
	 * 잠그지 않고 쓰기 권한만 확인한다(RE001·RE003). 외부 호출 전에 걸러 내거나 멱등 재전송을 확인할 때 쓴다.
	 * 실제 쓰기는 {@link #lockWritable} 로 다시 확인한다.
	 * @return 확인한 기록(재전송 응답의 지금 version·status 에 쓴다)
	 */
	@Transactional(readOnly = true)
	public TravelRecord checkWritable(Long recordId, Long userId) {
		return getWritable(recordId, userId).record();
	}

	/** {@link #checkWritable} 과 같고 방도 돌려준다(코스 미리보기 API-072 의 일차 계산). */
	@Transactional(readOnly = true)
	public RecordRoom getWritable(Long recordId, Long userId) {
		TravelRecord record = travelRecordRepository.findById(recordId)
			.orElseThrow(() -> new BusinessException(ExceptionType.RECORD_NOT_FOUND));
		TravelRoom room = liveRoom(record).filter(found -> isCurrentMember(found.getId(), userId))
			.orElseThrow(() -> new BusinessException(ExceptionType.RECORD_EDIT_FORBIDDEN));
		return new RecordRoom(record, room);
	}

	/** 기록 행을 잠그고 쓰기 권한을 확인한다. 호출한 트랜잭션 안에서만 쓴다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public RecordRoom lockWritable(Long recordId, Long userId) {
		TravelRecord record = travelRecordRepository.findByIdForUpdate(recordId)
			.orElseThrow(() -> new BusinessException(ExceptionType.RECORD_NOT_FOUND));
		TravelRoom room = liveRoom(record)
			.filter(found -> isCurrentMember(found.getId(), userId))
			.orElseThrow(() -> new BusinessException(ExceptionType.RECORD_EDIT_FORBIDDEN));
		return new RecordRoom(record, room);
	}

	/**
	 * 공개 코스 수정(API-075)용. 기록 행을 잠그고, 기록 없음·방 삭제 CO013, 현재 참여자 아님 CO008. 기록 쪽 예외를 잡아 바꾸면
	 * 트랜잭션이 rollback-only 가 되어 따로 둔다(명세 {@code specs/course-publish-api.md} 1-1).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public TravelRecord lockCourse(Long recordId, Long userId) {
		TravelRecord record = travelRecordRepository.findByIdForUpdate(recordId)
			.orElseThrow(() -> new BusinessException(ExceptionType.COURSE_NOT_FOUND));
		TravelRoom room = liveRoom(record).orElseThrow(() -> new BusinessException(ExceptionType.COURSE_NOT_FOUND));
		if (!isCurrentMember(room.getId(), userId)) {
			throw new BusinessException(ExceptionType.COURSE_NOT_MEMBER);
		}
		return record;
	}

	private Optional<TravelRoom> liveRoom(TravelRecord record) {
		return travelRoomRepository.findByIdAndDeletedAtIsNull(record.getRoomId());
	}

	private boolean isCurrentMember(Long roomId, Long userId) {
		return roomMemberRepository.findById(new RoomMemberId(roomId, userId)).filter(RoomMember::isCurrent).isPresent();
	}

	/** 기록과 그 방(삭제되지 않음). */
	public record RecordRoom(TravelRecord record, TravelRoom room) {
	}
}
