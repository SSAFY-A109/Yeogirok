package com.ssafy.yeogirok.domain.record.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.record.dto.RecordCompleteResponse;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.repository.ActualVisitRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** 여행기 완성(API-071). 코스 공개와 별개이고 자동으로 완성하지 않는다. 멱등키는 컨트롤러가 이 밖에서 처리한다. */
@Service
@RequiredArgsConstructor
public class RecordCompleteService {

	private final RecordFinder recordFinder;
	private final ActualVisitRepository actualVisitRepository;
	private final Clock clock;

	/**
	 * 기록 행을 잠근 뒤 권한(RE001·RE003) → version(C004) 을 보고, 이미 완성했으면 바꾸지 않고 지금 값을 준다(명세 5절 10).
	 * 실제 방문이 없거나 장소가 없는 방문이 있으면 RE016. 사진·이야기는 없어도 된다.
	 * visitDate·visitedAt 은 NOT NULL 이고 날짜 일치는 저장할 때(066·067) 입력 오프셋으로 검사했으므로 다시 보지 않는다
	 * (저장된 시각에는 입력 오프셋이 남지 않는다).
	 */
	@Transactional
	public RecordCompleteResponse complete(Long userId, Long recordId, int version) {
		TravelRecord record = recordFinder.lockWritable(recordId, userId).record();
		if (record.getVersion() != version) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		if (record.isCompleted()) {
			return RecordCompleteResponse.from(record);
		}
		if (actualVisitRepository.countByRecordId(recordId) == 0
			|| actualVisitRepository.countWithoutPlace(recordId) > 0) {
			throw new BusinessException(ExceptionType.RECORD_NOT_COMPLETABLE);
		}
		record.complete(clock.instant());
		return RecordCompleteResponse.from(record);
	}

	/** 같은 멱등키 재전송. 현재 쓰기 권한을 다시 확인하고 지금 기록 상태를 준다(version 을 다시 올리지 않는다). */
	@Transactional(readOnly = true)
	public RecordCompleteResponse getCompleted(Long userId, Long recordId) {
		return RecordCompleteResponse.from(recordFinder.checkWritable(recordId, userId));
	}
}
