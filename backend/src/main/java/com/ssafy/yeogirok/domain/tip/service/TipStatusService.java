package com.ssafy.yeogirok.domain.tip.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.tip.dto.TipStatusResponse;
import com.ssafy.yeogirok.domain.tip.publish.TipPublishStore;

import lombok.RequiredArgsConstructor;

/** API-050 내 팁 등록·게시 상태. 앱은 049 뒤 PUBLISHED + SUCCEEDED 가 될 때까지 이것을 조회한다. */
@Service
@RequiredArgsConstructor
public class TipStatusService {

	private final MyTipFinder myTipFinder;
	private final TipPublishStore publishStore;

	@Transactional(readOnly = true)
	public TipStatusResponse getStatus(Long userId, Long tipId) {
		return TipStatusResponse.of(myTipFinder.getOwn(tipId, userId), publishStore.find(tipId));
	}
}
