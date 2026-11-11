package com.ssafy.yeogirok.domain.record.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-063 항목(노션 063). extracted 는 OCR 전이어도 객체로 준다(값 null, 후보 빈 배열). 카드번호·주소·OCR 원문은 없다.
 * paidTime 은 HH:mm:ss(명세 specs/record-api-2.md 1-4).
 */
public record MaterialView(
	String materialId,
	String type,
	ReceiptMaterial.Status status,
	Extracted extracted,
	boolean needsReview,
	ReceiptMaterial.Error error,
	Integer version,
	String jobId,
	Values values,
	ReceiptMaterial.ReviewStatus reviewStatus,
	List<String> missingFields,
	boolean canUseForDraft,
	boolean originalAvailable,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant originalDeleteAfter,
	@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant uploadAcceptedAt
) {

	public record Extracted(
		String merchantName,
		List<RecordPlaceView> placeCandidates,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant paidAt,
		Double confidence,
		LocalDate paidDate,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime paidTime
	) {
	}

	public record Values(
		String merchantName,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant paidAt,
		RecordPlaceView place,
		LocalDate paidDate,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime paidTime
	) {

		public static Values of(ReceiptMaterial.Values values) {
			return new Values(values.merchantName(), values.paidAt(), values.place(), values.paidDate(),
				values.paidTime());
		}
	}

	/** @param material 끊긴 작업 판정을 마친 자료 */
	public static MaterialView of(String materialId, ReceiptMaterial material, Instant now) {
		ReceiptMaterial.Extracted extracted = material.extracted();
		ReceiptMaterial.Values values = material.values();
		return new MaterialView(materialId, material.type(), material.processStatus(),
			extracted == null ? new Extracted(null, List.of(), null, null, null, null)
				: new Extracted(extracted.merchantName(), extracted.placeCandidates(), extracted.paidAt(),
					extracted.confidence(), extracted.paidDate(), extracted.paidTime()),
			material.needsReview(), material.error(), material.version(), material.jobId(),
			Values.of(values),
			material.reviewStatus(), material.missingFields(), material.canUseForDraft(),
			material.isOriginalAvailable(now), material.deleteAfter(), material.uploadAcceptedAt());
	}
}
