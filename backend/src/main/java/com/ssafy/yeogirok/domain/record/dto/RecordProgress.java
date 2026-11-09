package com.ssafy.yeogirok.domain.record.dto;

import com.ssafy.yeogirok.domain.record.entity.RecordStatus;

/**
 * 기록 화면의 다음 할 일(노션 059 RecordProgress). 1차는 UPLOAD·EDIT·COMPLETED 만 쓴다(명세 5절 4).
 * RECEIPT_REVIEW·READY_TO_GENERATE·PROCESSING·REVIEW 와 draftKey·jobId 는 초안 API 때 채운다.
 */
public record RecordProgress(Stage stage, String draftKey, String jobId) {

	public enum Stage { UPLOAD, RECEIPT_REVIEW, READY_TO_GENERATE, PROCESSING, REVIEW, EDIT, COMPLETED }

	public static RecordProgress of(RecordStatus status, long visitCount) {
		Stage stage = status == RecordStatus.COMPLETED ? Stage.COMPLETED : visitCount > 0 ? Stage.EDIT : Stage.UPLOAD;
		return new RecordProgress(stage, null, null);
	}
}
