package com.ssafy.yeogirok.domain.tip.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.repository.ArTipRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

import lombok.RequiredArgsConstructor;

/** 작성자 본인 팁 조회(049 재전송·050). 위치 증거 없이 원격으로 본다. 없거나 삭제됐으면 T012, 남의 팁이면 T002. */
@Component
@RequiredArgsConstructor
public class MyTipFinder {

	private final ArTipRepository arTipRepository;

	@Transactional(readOnly = true)
	public ArTip getOwn(Long tipId, Long userId) {
		ArTip tip = arTipRepository.findByIdAndDeletedAtIsNull(tipId)
			.orElseThrow(() -> new BusinessException(ExceptionType.TIP_NOT_EXIST));
		if (!tip.getAuthorId().equals(userId)) {
			throw new BusinessException(ExceptionType.TIP_NOT_OWNER);
		}
		return tip;
	}
}
