package com.ssafy.yeogirok.domain.recommendation.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.recommendation.RecommendationPreferences;
import com.ssafy.yeogirok.domain.recommendation.dto.PreferenceResponse;
import com.ssafy.yeogirok.domain.recommendation.dto.PreferenceSaveCommand;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.service.RoomFinder;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 여행방 추천 조건 저장·조회(API-033·081). 현재 참여자 누구나 저장한다.
 * 조건은 자체 version 으로 동시 저장을 막고 여행방 version 은 올리지 않는다({@link TravelRoom#updatePreferences}).
 */
@Service
@RequiredArgsConstructor
public class RecommendationPreferenceService {

	private final RoomFinder roomFinder;
	private final Clock clock;

	/** API-081. 저장한 적 없으면 빈 조건과 version 1. */
	@Transactional(readOnly = true)
	public PreferenceResponse get(Long userId, Long roomId) {
		TravelRoom room = roomFinder.getForMember(roomId, userId).room();
		return PreferenceResponse.of(roomId, RecommendationPreferences.from(room.getPreferences()));
	}

	/** API-033. 방 행을 잠그고 조건 version 을 비교한다. 다르면 C004. 같은 값으로 다시 저장해도 version 은 오른다. */
	@Transactional
	public PreferenceResponse save(Long userId, Long roomId, PreferenceSaveCommand command) {
		TravelRoom room = roomFinder.getForMemberForUpdate(roomId, userId).room();
		RecommendationPreferences current = RecommendationPreferences.from(room.getPreferences());
		if (current.version() != command.version()) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		RecommendationPreferences next = current.next(command.regionCode(), command.categoryCodes());
		room.updatePreferences(next.toJson(), clock.instant());
		return PreferenceResponse.of(roomId, next);
	}
}
