package com.ssafy.yeogirok.domain.file.session;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;

/**
 * 영수증 자료(Redis {@code material:{materialId}}). 노션 ERD > Redis 4절 형식. 원본·OCR 원문·카드번호·주소는 넣지 않는다.
 * 연결·OCR·보정 칸은 기록 API(062·063·091·092)가 채운다(명세 {@code specs/record-api-2.md} 2-2). 새 칸이 없는 기존 JSON 도
 * 읽힌다(모두 null).
 *
 * @param jobId          최근 OCR 작업. API-062 가 접수하기 전에는 null
 * @param objectKey      확정 원본 경로. 원본을 지우면 비운다
 * @param deleteAfter    원본 삭제 기한(uploadAcceptedAt + 24시간). 늘리지 않는다
 * @param version        자료 version. 062 연결에서 1, OCR 결과 저장·091 보정·092 재시도 접수마다 +1. 연결 전 null
 * @param linkedAt       062 연결 시각. null 이면 아직 기록에 연결하지 않았다
 * @param extracted      최신 OCR 제안. OCR 전 null
 * @param reviewedValues 091 사용자 확정 값. 없으면 null
 */
public record ReceiptMaterial(
	String jobId,
	Long recordId,
	Long uploadedBy,
	String type,
	String objectKey,
	Status processStatus,
	String deleteStatus,
	Instant deleteAfter,
	Instant uploadAcceptedAt,
	Integer version,
	Instant linkedAt,
	Extracted extracted,
	Values reviewedValues,
	ReviewStatus reviewStatus,
	Error error
) {

	/** 처리 상태. 기존 JSON 의 문자열 값과 같다. */
	public enum Status { UPLOADED, EXTRACTING, READY, FAILED }

	public enum ReviewStatus { PENDING, REQUIRED, NOT_REQUIRED, CONFIRMED }

	public record Error(String code, String message) {
	}

	/**
	 * OCR 제안. paidAt 은 날짜·시각이 둘 다 있을 때만 +09:00 으로 조합한다(명세 5절 6). placeCandidates 는 TourAPI 전까지 비어 있다.
	 */
	public record Extracted(String merchantName, LocalDate paidDate, LocalTime paidTime, Instant paidAt,
		List<RecordPlaceView> placeCandidates, Double confidence) {

		public Extracted {
			placeCandidates = placeCandidates == null ? List.of() : List.copyOf(placeCandidates);
		}
	}

	/** 초안에 쓰는 값(091 확정 값이거나 OCR 제안). place 는 091 에서 정한다. */
	public record Values(String merchantName, Instant paidAt, LocalDate paidDate, LocalTime paidTime,
		RecordPlaceView place) {
	}

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

	/** API-086 영수증 확정 직후. */
	public static ReceiptMaterial uploaded(Long recordId, Long uploadedBy, String objectKey, Instant uploadAcceptedAt,
		Instant deleteAfter) {
		return new ReceiptMaterial(null, recordId, uploadedBy, "RECEIPT", objectKey, Status.UPLOADED, "PENDING",
			deleteAfter, uploadAcceptedAt, null, null, null, null, null, null);
	}

	/** 원본 삭제를 확인한 뒤. 경로를 비운다. */
	public ReceiptMaterial deleted() {
		return new ReceiptMaterial(jobId, recordId, uploadedBy, type, null, processStatus, "DELETED", deleteAfter,
			uploadAcceptedAt, version, linkedAt, extracted, reviewedValues, reviewStatus, error);
	}

	public boolean isLinked() {
		return linkedAt != null;
	}

	/** 기록에 연결했고 OCR 이 아직 끝나지 않았다(작업이 끊겼는지는 {@code ReceiptMaterials#current} 가 따로 본다). */
	public boolean isInProgress() {
		return isLinked() && (processStatus == Status.UPLOADED || processStatus == Status.EXTRACTING);
	}

	/** API-062 연결. version 1, OCR 대기. */
	public ReceiptMaterial linked(String type, String jobId, Instant now) {
		return new ReceiptMaterial(jobId, recordId, uploadedBy, type, objectKey, Status.UPLOADED, deleteStatus,
			deleteAfter, uploadAcceptedAt, 1, now, null, null, ReviewStatus.PENDING, null);
	}

	/** OCR 작업이 실행을 시작했다. version 은 그대로다. */
	public ReceiptMaterial extracting() {
		return with(jobId, Status.EXTRACTING, version, extracted, reviewedValues, reviewStatus, error);
	}

	/**
	 * OCR 성공. 사용자가 이미 확정했으면 그 값을 그대로 쓴다. OCR 제안에는 장소가 없어(TourAPI 후보 전까지, {@link #values}) 지금은
	 * NOT_REQUIRED 가 나오지 않고 모든 영수증이 091 확인을 거친다(의도). 후보가 붙으면 이 분기가 쓰인다.
	 */
	public ReceiptMaterial ocrSucceeded(Extracted result) {
		ReceiptMaterial next = with(jobId, Status.READY, version + 1, result, reviewedValues, reviewStatus, null);
		return next.withReviewStatus(reviewedValues != null ? ReviewStatus.CONFIRMED
			: next.missingFields().isEmpty() ? ReviewStatus.NOT_REQUIRED : ReviewStatus.REQUIRED);
	}

	/** OCR 실패. 091 로 직접 입력해 확정할 수 있다(명세 5절 8). */
	public ReceiptMaterial ocrFailed(Error failure) {
		return with(jobId, Status.FAILED, version + 1, extracted, reviewedValues,
			reviewedValues != null ? ReviewStatus.CONFIRMED : ReviewStatus.REQUIRED, failure);
	}

	/** API-091 사용자 확정. OCR 이 실패했던 자료도 READY 가 된다. */
	public ReceiptMaterial reviewed(Values values) {
		return with(jobId, Status.READY, version + 1, extracted, values, ReviewStatus.CONFIRMED, null);
	}

	/**
	 * API-092 재시도 접수. OCR 제안·사용자 확정 값은 그대로 둔다. OCR 이 다시 끝나기 전이라 보정 상태는 확정 값이 있으면
	 * CONFIRMED, 없으면 PENDING 으로 돌린다(처리 중인데 "보정 필요"로 보이지 않게, PR #84 리뷰).
	 */
	public ReceiptMaterial retryQueued(String newJobId) {
		return with(newJobId, Status.UPLOADED, version + 1, extracted, reviewedValues,
			reviewedValues != null ? ReviewStatus.CONFIRMED : ReviewStatus.PENDING, null);
	}

	/** 작업이 끊겨(만료·기한 초과) 실패로 보여 줄 때. 저장하지 않는 값이라 version 을 올리지 않는다. */
	public ReceiptMaterial interrupted(Error failure) {
		return with(jobId, Status.FAILED, version, extracted, reviewedValues,
			reviewedValues != null ? ReviewStatus.CONFIRMED : ReviewStatus.REQUIRED, failure);
	}

	/** 초안에 쓰는 값. 사용자 확정 값이 있으면 그것, 없으면 OCR 제안(장소는 정하지 않는다, 명세 2-3). */
	public Values values() {
		if (reviewedValues != null) {
			return reviewedValues;
		}
		return extracted == null ? new Values(null, null, null, null, null)
			: new Values(extracted.merchantName(), extracted.paidAt(), extracted.paidDate(), extracted.paidTime(), null);
	}

	/** values 중 비어 있는 필수값 이름. */
	public List<String> missingFields() {
		Values values = values();
		List<String> missing = new ArrayList<>();
		if (values.merchantName() == null) {
			missing.add("merchantName");
		}
		if (values.paidDate() == null) {
			missing.add("paidDate");
		}
		if (values.paidTime() == null) {
			missing.add("paidTime");
		}
		if (values.place() == null) {
			missing.add("place");
		}
		return missing;
	}

	public boolean needsReview() {
		return reviewStatus == ReviewStatus.REQUIRED;
	}

	public boolean canUseForDraft() {
		return processStatus == Status.READY
			&& (reviewStatus == ReviewStatus.NOT_REQUIRED || reviewStatus == ReviewStatus.CONFIRMED);
	}

	private ReceiptMaterial withReviewStatus(ReviewStatus status) {
		return with(jobId, processStatus, version, extracted, reviewedValues, status, error);
	}

	private ReceiptMaterial with(String jobId, Status status, Integer version, Extracted extracted,
		Values reviewedValues, ReviewStatus reviewStatus, Error error) {
		return new ReceiptMaterial(jobId, recordId, uploadedBy, type, objectKey, status, deleteStatus, deleteAfter,
			uploadAcceptedAt, version, linkedAt, extracted, reviewedValues, reviewStatus, error);
	}
}
