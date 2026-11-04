package com.ssafy.yeogirok.domain.footprint.service;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.footprint.entity.Footprint;
import com.ssafy.yeogirok.domain.footprint.repository.FootprintRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** 발자취 본인 확인. 없거나 삭제됐으면 404 F001, 남의 것이면 403 F002. */
@Component
@RequiredArgsConstructor
public class FootprintFinder {

	private final FootprintRepository footprintRepository;

	@Transactional(readOnly = true)
	public Footprint getOwnFootprint(Long footprintId, Long userId) {
		return verifyOwner(footprintRepository.findByIdAndDeletedAtIsNull(footprintId), userId);
	}

	/** {@link #getOwnFootprint} 와 같지만 발자취 행을 잠근다. 호출한 트랜잭션 안에서만 쓴다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public Footprint getOwnFootprintForUpdate(Long footprintId, Long userId) {
		return verifyOwner(footprintRepository.findForUpdateByIdAndDeletedAtIsNull(footprintId), userId);
	}

	private static Footprint verifyOwner(Optional<Footprint> found, Long userId) {
		Footprint footprint = found.orElseThrow(() -> new BusinessException(ExceptionType.FOOTPRINT_NOT_FOUND));
		if (!footprint.isOwnedBy(userId)) {
			throw new BusinessException(ExceptionType.FOOTPRINT_NOT_OWNER);
		}
		return footprint;
	}
}
