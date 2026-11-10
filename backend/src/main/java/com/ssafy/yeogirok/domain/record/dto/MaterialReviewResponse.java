package com.ssafy.yeogirok.domain.record.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;

/** API-091 응답(노션 091). values 는 063 과 같은 모양이다. */
public record MaterialReviewResponse(
	String materialId,
	Integer version,
	ReceiptMaterial.ReviewStatus reviewStatus,
	boolean needsReview,
	List<String> missingFields,
	MaterialView.Values values,
	ReceiptMaterial.Status status,
	boolean canUseForDraft
) {

	public static MaterialReviewResponse of(String materialId, ReceiptMaterial material) {
		return new MaterialReviewResponse(materialId, material.version(), material.reviewStatus(),
			material.needsReview(), material.missingFields(), MaterialView.Values.of(material.values()),
			material.processStatus(), material.canUseForDraft());
	}
}
