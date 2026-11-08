package com.ssafy.yeogirok.domain.tip.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.tip.TipProperties;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.location.LocationEvidence;

import lombok.RequiredArgsConstructor;

/** 현장 공개 팁 조회(API-048·052). 게시됐고 삭제되지 않은 팁만, 아니면 T001. 5m 밖이면 T004. */
@Component
@RequiredArgsConstructor
public class PublicTipFinder {

	private final ArTipRepository arTipRepository;
	private final TipProperties properties;

	/** 위치 증거 값 검사({@code LocationEvidencePolicy#verify})는 부르는 쪽이 먼저 한다. 탐색 결과와 상관없이 매번 거리를 본다. */
	@Transactional(readOnly = true)
	public ArTip getInteractable(Long tipId, LocationEvidence location) {
		ArTip tip = arTipRepository.findByIdAndStatusAndDeletedAtIsNull(tipId, TipStatus.PUBLISHED)
			.orElseThrow(() -> new BusinessException(ExceptionType.TIP_NOT_FOUND));
		if (!properties.canInteract(tip.distanceMetersFrom(location.latitude(), location.longitude()))) {
			throw new BusinessException(ExceptionType.TIP_OUT_OF_RANGE);
		}
		return tip;
	}
}
