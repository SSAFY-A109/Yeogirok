package com.ssafy.yeogirok.domain.record.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.draft.RecordDraft;
import com.ssafy.yeogirok.domain.record.dto.RecordProgress.Stage;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;

/** progress 단계 판정(명세 specs/record-api-2.md 4-10). 위에서부터 처음 맞는 단계다. */
class RecordProgressTest {

	static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
	static final RecordPlaceView CAFE = new RecordPlaceView(PlaceProvider.MANUAL, null, "카페", BigDecimal.ONE,
		BigDecimal.ONE, null, null, null, null);

	static ReceiptMaterial uploaded() {
		return ReceiptMaterial.uploaded(1L, 7L, "receipt/u", NOW, NOW.plus(Duration.ofHours(24)))
			.linked("RECEIPT", "job", NOW);
	}

	static final ReceiptMaterial IN_PROGRESS = uploaded();
	static final ReceiptMaterial NEEDS_REVIEW = uploaded().ocrFailed(new ReceiptMaterial.Error("M003", "x"));
	static final ReceiptMaterial USABLE = NEEDS_REVIEW.reviewed(new ReceiptMaterial.Values("카페", NOW,
		LocalDate.of(2026, 11, 10), LocalTime.NOON, CAFE));

	static RecordDraft draft() {
		return RecordDraft.queued("d-1", "job-1", 1L, 7L, 1,
			new RecordDraft.SourceSnapshot(List.of(), List.of(), List.of()), NOW, NOW.plusSeconds(720));
	}

	static RecordProgress of(RecordStatus status, long visits, RecordDraft draft, List<ReceiptMaterial> mine,
		long photos) {
		return RecordProgress.of(status, visits, draft, mine, photos);
	}

	@Test
	void 완성이면_COMPLETED() {
		assertThat(of(RecordStatus.COMPLETED, 3, draft(), List.of(NEEDS_REVIEW), 1).stage()).isEqualTo(Stage.COMPLETED);
	}

	@Test
	void 최근_초안이_진행_중이면_PROCESSING_이고_초안_키와_작업을_준다() {
		assertThat(of(RecordStatus.DRAFT, 3, draft(), List.of(), 0))
			.isEqualTo(new RecordProgress(Stage.PROCESSING, "d-1", "job-1"));
		assertThat(of(RecordStatus.DRAFT, 3, draft().running(), List.of(), 0).stage()).isEqualTo(Stage.PROCESSING);
	}

	@Test
	void 성공했고_반영_전이면_REVIEW_반영했으면_다음_단계() {
		RecordDraft succeeded = draft().succeeded(List.of(), List.of());

		assertThat(of(RecordStatus.DRAFT, 0, succeeded, List.of(), 0))
			.isEqualTo(new RecordProgress(Stage.REVIEW, "d-1", "job-1"));
		assertThat(of(RecordStatus.DRAFT, 2, succeeded.markApplied(), List.of(), 0).stage()).isEqualTo(Stage.EDIT);
	}

	@Test
	void 실패한_초안은_건너뛰고_방문이_있으면_EDIT() {
		RecordDraft failed = draft().failed(new JobError("C014", "x"));

		assertThat(of(RecordStatus.DRAFT, 1, failed, List.of(NEEDS_REVIEW), 1))
			.isEqualTo(new RecordProgress(Stage.EDIT, null, null));
	}

	@Test
	void 방문이_없고_보정할_영수증이_있으면_RECEIPT_REVIEW() {
		assertThat(of(RecordStatus.DRAFT, 0, null, List.of(USABLE, NEEDS_REVIEW), 1).stage())
			.isEqualTo(Stage.RECEIPT_REVIEW);
	}

	@Test
	void 쓸_수_있는_영수증과_사진이_각각_있으면_READY_TO_GENERATE() {
		assertThat(of(RecordStatus.DRAFT, 0, null, List.of(USABLE, IN_PROGRESS), 1).stage())
			.isEqualTo(Stage.READY_TO_GENERATE);
	}

	@Test
	void 그_밖에는_UPLOAD() {
		assertThat(of(RecordStatus.DRAFT, 0, null, List.of(USABLE), 0).stage()).isEqualTo(Stage.UPLOAD);
		assertThat(of(RecordStatus.DRAFT, 0, null, List.of(IN_PROGRESS), 3).stage()).isEqualTo(Stage.UPLOAD);
		assertThat(of(RecordStatus.DRAFT, 0, null, List.of(), 3)).isEqualTo(new RecordProgress(Stage.UPLOAD, null, null));
	}
}
