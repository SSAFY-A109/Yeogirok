package com.ssafy.yeogirok.domain.tip.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.ssafy.yeogirok.domain.tip.entity.ArTip;
import com.ssafy.yeogirok.domain.tip.entity.SpatialReference;
import com.ssafy.yeogirok.domain.tip.publish.TipEditDraft.Relocation;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.location.LocationEvidence;
import com.ssafy.yeogirok.global.location.LocationEvidencePolicy;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/** AR 재배치 검사(API-054·051). 위치 증거 C008~C011 → (기존 팁 5m 밖 T004) → spatialReference·placement T007·C001. */
@Component
@RequiredArgsConstructor
public class TipRelocations {

	private final LocationEvidencePolicy locationEvidencePolicy;
	private final PublicTipFinder publicTipFinder;
	private final TipPlacements tipPlacements;

	/** @param nearTip 기존 팁에서 5m 안이어야 하는지(공개 팁 수정 true, 최초 게시 재시도는 049 와 같이 false) */
	public Relocation validate(ArTip tip, LocationEvidence location, JsonNode spatialReference, JsonNode placement,
		Instant now, boolean nearTip) {
		locationEvidencePolicy.verify(location, now);
		if (nearTip) {
			publicTipFinder.checkInteractable(tip, location);
		}
		return new Relocation(location, locationEvidencePolicy.policyVersion(),
			SpatialReference.read(spatialReference, now), tipPlacements.toMap(placement));
	}

	/** 저장해 둔 재배치가 아직 쓸 수 있는지(051). 위치 증거가 오래됐으면 C009, 공간 참조가 만료됐으면 T007 — 새 재배치 정보를 받는다. */
	public void checkStillValid(Relocation relocation, Instant now) {
		locationEvidencePolicy.verify(relocation.location(), now);
		checkNotExpired(relocation.spatialReference(), now);
	}

	/** @throws BusinessException 공간 참조 만료 시각이 지났으면 T007 */
	public static void checkNotExpired(SpatialReference spatialReference, Instant now) {
		if (spatialReference.expiresAt() != null && !spatialReference.expiresAt().isAfter(now)) {
			throw new BusinessException(ExceptionType.TIP_SPATIAL_INVALID,
				"spatialReference: 공간 참조가 만료됐습니다. location·spatialReference·placement 를 다시 보내 주세요.");
		}
	}
}
