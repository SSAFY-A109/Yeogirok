package com.ssafy.yeogirok.domain.record.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.record.dto.RecordCreateResponse;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.TravelRecordRepository;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;

import lombok.RequiredArgsConstructor;

/** 여행방에서 기록 공간 만들기(API-057). 멱등키는 컨트롤러가 이 밖에서 처리한다. */
@Service
@RequiredArgsConstructor
public class RecordCreateService {

	private final RoomFinder roomFinder;
	private final TravelRecordRepository travelRecordRepository;
	private final ReferencePlans referencePlans;
	private final Clock clock;

	/**
	 * 방 행을 잠근 뒤 참여(RO001·RO002) → 종료일(RE024) → 방당 1개(RE017) 순으로 확인하고 만든다.
	 * 기록 쓰기 중 방 행을 잠그는 것은 이 API 하나뿐이다(명세 1-2). 같은 잠금 안에서 확정 일정을 참고 계획으로 복사한다(노션 057).
	 *
	 * @param title 생략이면 null(여행방 제목을 쓴다)
	 */
	@Transactional
	public RecordCreateResponse create(Long userId, Long roomId, String title) {
		TravelRoom room = roomFinder.getForMemberForUpdate(roomId, userId).room();
		LocalDate today = LocalDate.ofInstant(clock.instant(), ApiDateTime.ZONE);
		if (room.getEndDate() == null || !room.getEndDate().isBefore(today)) {
			throw new BusinessException(ExceptionType.TRIP_NOT_ENDED);
		}
		if (travelRecordRepository.existsByRoomId(roomId)) {
			throw new BusinessException(ExceptionType.RECORD_ALREADY_EXISTS);
		}
		TravelRecord record = TravelRecord.create(roomId, title == null ? room.getTitle() : title, clock.instant());
		record.attachReferencePlan(referencePlans.copy(room));
		return RecordCreateResponse.from(travelRecordRepository.save(record));
	}

	/** 같은 멱등키 재전송. 현재 참여만 다시 확인하고 종료일은 보지 않는다(노션 057). */
	@Transactional(readOnly = true)
	public RecordCreateResponse getCreated(Long userId, Long roomId, Long recordId) {
		roomFinder.getForMember(roomId, userId);
		return travelRecordRepository.findById(recordId).map(RecordCreateResponse::from)
			.orElseThrow(() -> new BusinessException(ExceptionType.RECORD_NOT_FOUND));
	}
}
