package com.ssafy.yeogirok.domain.footprint.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.domain.footprint.repository.FootprintRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * 개인 기록 본인 확인. 발자취는 작성자 본인만 조회·수정·삭제한다.
 *
 * <p>없거나 삭제됐으면 404 F001, 있지만 본인 것이 아니면 403 F002.
 */
@Component
@RequiredArgsConstructor
public class FootprintFinder {

	private final FootprintRepository footprintRepository;

	@Transactional(readOnly = true)
	public Footprint getOwnFootprint(Long footprintId, Long userId) {
		Footprint footprint = footprintRepository.findByIdAndDeletedAtIsNull(footprintId)
			.orElseThrow(() -> new BusinessException(ExceptionType.FOOTPRINT_NOT_FOUND));
		if (!footprint.isOwnedBy(userId)) {
			throw new BusinessException(ExceptionType.FOOTPRINT_NOT_OWNER);
		}
		return footprint;
	}
}
