package com.ssafy.yeogirok.domain.footprint.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.footprint.dto.FootprintDetailResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FootprintService {

	private final FootprintFinder footprintFinder;

	/** API-043. 본인 것만, 삭제한 것은 F001. */
	@Transactional(readOnly = true)
	public FootprintDetailResponse getFootprint(Long userId, Long footprintId) {
		return FootprintDetailResponse.from(footprintFinder.getOwnFootprint(footprintId, userId));
	}
}
