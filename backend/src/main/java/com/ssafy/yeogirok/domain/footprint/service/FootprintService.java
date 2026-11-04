package com.ssafy.yeogirok.domain.footprint.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;
import com.ssafy.yeogirok.domain.footprint.dto.FootprintUpdateResponse;
import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.domain.footprint.entity.FootprintBody;
import com.ssafy.yeogirok.domain.footprint.repository.FootprintRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FootprintService {

	private final FootprintFinder footprintFinder;
	private final FootprintRepository footprintRepository;

	/** API-043. 본인 것만, 삭제한 것은 F001. */
	@Transactional(readOnly = true)
	public FootprintDetailResponse getFootprint(Long userId, Long footprintId) {
		return FootprintDetailResponse.from(footprintFinder.getOwnFootprint(footprintId, userId));
	}

	/**
	 * API-044. 본문만 바꾸고 version 을 1 올린다.
	 *
	 * <p>요청 version 이 현재와 다르면 C004. 같은 version 으로 동시에 들어온 요청은 @Version 이 막고,
	 * 그 예외도 GlobalExceptionHandler 가 C004 로 바꾼다.
	 */
	@Transactional
	public FootprintUpdateResponse updateBody(Long userId, Long footprintId, int version, String body) {
		FootprintBody.normalize(body);  // 본문 길이 오류(C003)는 요청값 문제라 조회보다 먼저 알린다
		Footprint footprint = footprintFinder.getOwnFootprint(footprintId, userId);
		if (footprint.getVersion() != version) {
			throw new BusinessException(ExceptionType.VERSION_MISMATCH);
		}
		footprint.updateBody(body);
		// 응답에 올라간 version·updatedAt 을 담으려고 지금 반영한다.
		footprintRepository.flush();
		return FootprintUpdateResponse.from(footprint);
	}
}
