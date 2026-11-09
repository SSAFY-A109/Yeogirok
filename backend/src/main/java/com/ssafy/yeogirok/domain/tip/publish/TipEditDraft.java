package com.ssafy.yeogirok.domain.tip.publish;

import java.util.Map;

import com.ssafy.yeogirok.domain.tip.entity.ArTipCategory;
import com.ssafy.yeogirok.domain.tip.entity.SpatialReference;
import com.ssafy.yeogirok.global.location.LocationEvidence;

/**
 * API-054 수정안(Redis {@code tip-edit:{tipId}:{jobId}}). 검증을 마친 바꿀 값만 둔다. 작업 상태는 {@link TipPublishState} 에 있다.
 *
 * @param category   null 이면 그대로
 * @param body       정리한 본문. null 이면 그대로
 * @param imagePath  photo 가 REPLACE 일 때 새 사진의 확정 경로
 * @param relocation AR 재배치. null 이면 그대로
 */
public record TipEditDraft(
	Long authorId,
	int baseVersion,
	ArTipCategory category,
	String body,
	PhotoChange photo,
	String imagePath,
	Relocation relocation
) {

	public enum PhotoChange { KEEP, REMOVE, REPLACE }

	public record Relocation(LocationEvidence location, String locationPolicyVersion, SpatialReference spatialReference,
		Map<String, Object> placement) {
	}

	public boolean photoChanged() {
		return photo != PhotoChange.KEEP;
	}
}
