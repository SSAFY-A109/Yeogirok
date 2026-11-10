package com.ssafy.yeogirok.domain.record.dto;

import java.util.List;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;

/**
 * 기록 화면의 다음 할 일(노션 059 RecordProgress). 노션에 단계 판정 규칙이 없어 명세 {@code specs/record-api-2.md} 4-10 순서로
 * 정한다(5절 18). 영수증 단계는 요청자 본인 자료로만 본다. draftKey·jobId 는 PROCESSING·REVIEW 일 때만 채운다.
 */
public record RecordProgress(Stage stage, String draftKey, String jobId) {

	public enum Stage { UPLOAD, RECEIPT_REVIEW, READY_TO_GENERATE, PROCESSING, REVIEW, EDIT, COMPLETED }

	/**
	 * @param latestDraft 그 기록의 최근 통합 초안(기한 판정을 마친 값). 없으면 null
	 * @param myMaterials 요청자 본인이 연결한 영수증 자료(끊긴 작업 판정을 마친 값)
	 * @param photoCount  기록 사진 수
	 */
	public static RecordProgress of(RecordStatus status, long visitCount, RecordDraft latestDraft,
		List<ReceiptMaterial> myMaterials, long photoCount) {
		if (status == RecordStatus.COMPLETED) {
			return stage(Stage.COMPLETED);
		}
		if (latestDraft != null && !latestDraft.status().isFinished()) {
			return new RecordProgress(Stage.PROCESSING, latestDraft.draftKey(), latestDraft.jobId());
		}
		if (latestDraft != null && latestDraft.status() == JobStatus.SUCCEEDED && !latestDraft.applied()) {
			return new RecordProgress(Stage.REVIEW, latestDraft.draftKey(), latestDraft.jobId());
		}
		if (visitCount > 0) {
			return stage(Stage.EDIT);
		}
		if (myMaterials.stream().anyMatch(ReceiptMaterial::needsReview)) {
			return stage(Stage.RECEIPT_REVIEW);
		}
		if (photoCount > 0 && myMaterials.stream().anyMatch(ReceiptMaterial::canUseForDraft)) {
			return stage(Stage.READY_TO_GENERATE);
		}
		return stage(Stage.UPLOAD);
	}

	private static RecordProgress stage(Stage stage) {
		return new RecordProgress(stage, null, null);
	}
}
