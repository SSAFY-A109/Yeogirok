package com.ssafy.yeogirok.domain.record.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;

/** API-059 materials 항목. 요청자 본인 자료의 요약만 담는다(값·원본 정보는 063). */
public record MaterialSummary(
	String materialId,
	Integer version,
	String type,
	ReceiptMaterial.Status status,
	ReceiptMaterial.ReviewStatus reviewStatus,
	boolean needsReview,
	List<String> missingFields,
	boolean canUseForDraft,
	ReceiptMaterial.Error error
) {

	/** @param material 끊긴 작업 판정을 마친 자료 */
	public static MaterialSummary of(String materialId, ReceiptMaterial material) {
		return new MaterialSummary(materialId, material.version(), material.type(), material.processStatus(),
			material.reviewStatus(), material.needsReview(), material.missingFields(), material.canUseForDraft(),
			material.error());
	}
}
