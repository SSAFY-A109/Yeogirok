package com.ssafy.yeogirok.domain.file.session;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * 영수증 자료(Redis {@code material:{materialId}}). 노션 ERD > Redis 4절 형식. 원본·OCR 전문은 넣지 않는다.
 * OCR·보정 필드(version, extracted, reviewStatus 등)는 기록 API(062·063·091)가 채운다.
 *
 * @param jobId      OCR 작업. API-062 가 접수하기 전에는 null
 * @param objectKey  확정 원본 경로. 원본을 지우면 비운다
 * @param deleteAfter 원본 삭제 기한(uploadAcceptedAt + 24시간). 늘리지 않는다
 */
public record ReceiptMaterial(
	String jobId,
	Long recordId,
	Long uploadedBy,
	String type,
	String objectKey,
	String processStatus,
	String deleteStatus,
	Instant deleteAfter,
	Instant uploadAcceptedAt
) {

	/** 원본을 아직 내려줄 수 있다: 지우지 않았고 삭제 기한 전이다(기한이 지나면 물리 삭제 전이어도 막는다, 노션 API-013). */
	public boolean isOriginalAvailable(Instant now) {
		return objectKey != null && !"DELETED".equals(deleteStatus) && now.isBefore(deleteAfter);
	}

	/**
	 * uploadId 로 정해지는 다른 UUID. 자료 저장 뒤 세션 갱신이 실패해 완료를 다시 처리해도 같은 자료를 덮어쓰고,
	 * 정리 작업은 지운 원본 경로(receipt/{uploadId})로 자료를 찾는다.
	 */
	public static String idOf(String uploadId) {
		return UUID.nameUUIDFromBytes(("material:" + uploadId).getBytes(StandardCharsets.UTF_8)).toString();
	}

	/** 원본 삭제를 확인한 뒤. 경로를 비운다. */
	public ReceiptMaterial deleted() {
		return new ReceiptMaterial(jobId, recordId, uploadedBy, type, null, processStatus, "DELETED", deleteAfter,
			uploadAcceptedAt);
	}

	/** API-086 영수증 확정 직후. */
	public static ReceiptMaterial uploaded(Long recordId, Long uploadedBy, String objectKey, Instant uploadAcceptedAt,
		Instant deleteAfter) {
		return new ReceiptMaterial(null, recordId, uploadedBy, "RECEIPT", objectKey, "UPLOADED", "PENDING", deleteAfter,
			uploadAcceptedAt);
	}
}
