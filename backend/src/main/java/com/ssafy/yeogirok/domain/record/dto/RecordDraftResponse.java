package com.ssafy.yeogirok.domain.record.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * API-065 응답(노션 065). 진행 중·실패면 items [], draftVersion null(명세 specs/record-api-2.md 5절 16). storyDraft 는 없다.
 * sourcePriority 는 같은 방문 안에서 필드 값이 부딪힐 때 고르는 순서다(정렬·포함 기준 아님).
 */
public record RecordDraftResponse(String draftKey, JobStatus status, int baseVersion, List<Item> items,
	List<String> warnings, JobError error, Integer draftVersion, List<RecordDraft.SourceType> sourcePriority) {

	private static final List<RecordDraft.SourceType> SOURCE_PRIORITY = List.of(RecordDraft.SourceType.MANUAL,
		RecordDraft.SourceType.RECEIPT, RecordDraft.SourceType.PHOTO, RecordDraft.SourceType.PLAN);

	public record Item(int itemId, List<String> sourceMaterialIds, List<RecordPlaceView> placeCandidates,
		LocalDate visitDate,
		@JsonFormat(pattern = ApiDateTime.PATTERN, timezone = ApiDateTime.TIME_ZONE) Instant proposedVisitedAt,
		boolean needsReview, RecordDraft.Evidence evidence, List<Long> sourcePhotoIds, Long plannedItemId,
		RecordPlaceView proposedPlace) {

		static Item of(RecordDraft.Item item) {
			return new Item(item.itemId(), item.sourceMaterialIds(), item.placeCandidates(), item.visitDate(),
				item.proposedVisitedAt(), item.needsReview(), item.evidence(), item.sourcePhotoIds(), item.plannedItemId(),
				item.proposedPlace());
		}
	}

	/** @param draft 기한 판정({@link RecordDraft#asOf})을 마친 초안 */
	public static RecordDraftResponse of(RecordDraft draft) {
		boolean succeeded = draft.status() == JobStatus.SUCCEEDED;
		return new RecordDraftResponse(draft.draftKey(), draft.status(), draft.baseVersion(),
			succeeded ? draft.items().stream().map(Item::of).toList() : List.of(),
			draft.warnings(), draft.error(), succeeded ? draft.draftVersion() : null, SOURCE_PRIORITY);
	}
}
