package com.ssafy.yeogirok.domain.tip.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.tip.dto.TipDeleteResponse;
import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.TipStatus;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/**
 * API-055 내 팁 원격 삭제(위치 증거 없음). 사진 파일·앵커 물리 삭제는 하지 않는다(별도 작업).
 * 진행 중 게시·수정 작업은 저장 직전에 같은 행 잠금으로 다시 읽어 삭제된 팁을 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TipDeleteService {

	private final ArTipRepository arTipRepository;
	private final Clock clock;

	/** 없으면 T012, 남의 팁 T002, version 불일치 C004. 이미 삭제했으면 같은 성공 결과(version 그대로). */
	@Transactional
	public TipDeleteResponse delete(Long userId, Long tipId, int version) {
		ArTip tip = arTipRepository.findForUpdateById(tipId)
			.orElseThrow(() -> new BusinessException(ExceptionType.TIP_NOT_EXIST));
		if (!tip.getAuthorId().equals(userId)) {
			throw new BusinessException(ExceptionType.TIP_NOT_OWNER);
		}
		if (!tip.isDeleted()) {
			if (tip.getVersion() != version) {
				throw new BusinessException(ExceptionType.VERSION_MISMATCH);
			}
			tip.delete(clock.instant());
		}
		return new TipDeleteResponse(tipId, TipStatus.DELETED);
	}
}
