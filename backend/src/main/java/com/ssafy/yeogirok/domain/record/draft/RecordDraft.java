package com.ssafy.yeogirok.domain.record.draft;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;

/**
 * 통합 기록 초안(Redis {@code draft:{draftKey}}, 명세 {@code specs/record-api-2.md} 2-5). 영수증은 정제 값(values)만 담는다.
 * 서버가 초안을 다시 고치는 API 가 없어 draftVersion 은 성공하면 1 이고 바뀌지 않는다.
 *
 * @param deadline 접수 시각 + 작업 최대 수명. 진행 중인데 이 시각을 넘기면 실패로 본다({@link #asOf})
 * @param applied  API-066 반영 커밋 뒤 true
 */
public record RecordDraft(String draftKey, String type, String jobId, Long recordId, Long requestedBy,
	int baseVersion, JobStatus status, Integer draftVersion, SourceSnapshot sourceSnapshot, List<Item> items,
	List<String> warnings, JobError error, boolean applied, Instant createdAt, Instant deadline) {

	public static final String TYPE = "RECORD";
	/** EXIF 를 읽지 못한 사진이 있다. 초안에 한 번만 넣는다. */
	public static final String PHOTO_METADATA_MISSING = "PHOTO_METADATA_MISSING";
	/** 사진만으로는 장소를 정하지 않았다(PHOTO 항목). */
	public static final String PHOTO_PLACE_UNRESOLVED = "PHOTO_PLACE_UNRESOLVED";
	/** 계획에만 있는 항목이라 실제로 갔는지·언제 갔는지 모른다(PLAN 항목). */
	public static final String PLAN_NOT_CONFIRMED = "PLAN_NOT_CONFIRMED";

	public enum SourceType { MANUAL, RECEIPT, PHOTO, PLAN }

	/** 접수 때 고정한 입력. plannedItemIds 는 참고 계획 사본 항목 중 아직 방문에 연결되지 않은 것(사본이 없으면 빈 목록). */
	public record SourceSnapshot(List<Long> imageIds, List<SnapshotMaterial> materials, List<Long> plannedItemIds) {

		public SourceSnapshot {
			imageIds = List.copyOf(imageIds);
			materials = List.copyOf(materials);
			plannedItemIds = plannedItemIds == null ? List.of() : List.copyOf(plannedItemIds);
		}
	}

	public record SnapshotMaterial(String materialId, int version, ReceiptMaterial.Values values) {
	}

	/** 노션 065 items[]. storyDraft 는 없다. plannedItemId 는 PLAN 항목에만 있다(영수증·사진 항목과 합치지 않음). */
	public record Item(int itemId, List<String> sourceMaterialIds, List<RecordPlaceView> placeCandidates,
		LocalDate visitDate, Instant proposedVisitedAt, boolean needsReview, Evidence evidence, List<Long> sourcePhotoIds,
		Long plannedItemId, RecordPlaceView proposedPlace) {
	}

	public record Evidence(List<SourceType> sourceTypes, FieldSources fieldSources, List<String> warnings) {
	}

	public record FieldSources(SourceType place, SourceType visitDate, SourceType proposedVisitedAt) {
	}

	public RecordDraft {
		Objects.requireNonNull(draftKey, "draftKey");
		Objects.requireNonNull(recordId, "recordId");
		Objects.requireNonNull(status, "status");
		items = items == null ? List.of() : List.copyOf(items);
		warnings = warnings == null ? List.of() : List.copyOf(warnings);
	}

	public static RecordDraft queued(String draftKey, String jobId, Long recordId, Long requestedBy, int baseVersion,
		SourceSnapshot sourceSnapshot, Instant now, Instant deadline) {
		return new RecordDraft(draftKey, TYPE, jobId, recordId, requestedBy, baseVersion, JobStatus.QUEUED, null,
			sourceSnapshot, List.of(), List.of(), null, false, now, deadline);
	}

	public RecordDraft running() {
		return with(JobStatus.RUNNING, null, items, warnings, null);
	}

	public RecordDraft succeeded(List<Item> newItems, List<String> newWarnings) {
		return with(JobStatus.SUCCEEDED, 1, newItems, newWarnings, null);
	}

	/** API-066 반영 커밋 뒤. */
	public RecordDraft markApplied() {
		return new RecordDraft(draftKey, type, jobId, recordId, requestedBy, baseVersion, status, draftVersion,
			sourceSnapshot, items, warnings, error, true, createdAt, deadline);
	}

	public RecordDraft failed(JobError failure) {
		return with(JobStatus.FAILED, null, List.of(), List.of(), failure);
	}

	/** 진행 중인데 기한을 넘겼으면 실패로 본다(서버가 처리 도중 재시작). 저장값은 바꾸지 않는다. */
	public RecordDraft asOf(Instant now) {
		if (status.isFinished() || deadline == null || !now.isAfter(deadline)) {
			return this;
		}
		return failed(Job.TIMED_OUT);
	}

	private RecordDraft with(JobStatus newStatus, Integer newDraftVersion, List<Item> newItems, List<String> newWarnings,
		JobError newError) {
		return new RecordDraft(draftKey, type, jobId, recordId, requestedBy, baseVersion, newStatus, newDraftVersion,
			sourceSnapshot, newItems, newWarnings, newError, applied, createdAt, deadline);
	}
}
