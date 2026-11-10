package com.ssafy.yeogirok.domain.record.draft;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.job.service.JobStore;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.draft.PhotoMetadataReader.PhotoMetadata;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftTestConfig.FakePhotoMetadataReader;
import com.ssafy.yeogirok.domain.record.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.record.dto.DraftCreateCommand.MaterialRef;
import com.ssafy.yeogirok.domain.record.dto.DraftJobResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.entity.RecordStatus;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.HoldableExecutor;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;

/** API-064 접수와 생성 작업(명세 specs/record-api-2.md 4-7). 항목 조합 규칙 자체는 RecordDraftComposerTest 가 본다. */
@DataJpaTest
@Import(RecordDraftTestConfig.class)
class RecordDraftServiceTest {

	static final Long ME = 9501L;
	static final Long FRIEND = 9502L;
	static final Long LEFT = 9503L;
	static final String MATERIAL_ID = "11111111-2222-4333-8444-555555555555";
	static final Instant PAID_AT = Instant.parse("2026-11-02T03:00:00Z");
	static final RecordPlaceView CAFE = new RecordPlaceView(PlaceProvider.MANUAL, null, "안목 카페",
		new BigDecimal("37.7700000"), new BigDecimal("128.9470000"), null, null, null, null);

	@Autowired
	RecordDraftService draftService;
	@Autowired
	RecordDraftStore draftStore;
	@Autowired
	ReceiptMaterialStore materialStore;
	@Autowired
	JobStore jobStore;
	@Autowired
	FileStorage fileStorage;
	@Autowired
	FakePhotoMetadataReader metadataReader;
	@Autowired
	HoldableExecutor executor;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;

	TravelRoom room;
	Long recordId;
	Long imageId;
	Long otherRecordImageId;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock.set(NOW);
		executor.reset();
		metadataReader.clear();
		given(fileStorage.readPrefix(eq("photos"), anyString(), anyInt()))
			.willAnswer(invocation -> invocation.<String>getArgument(1).getBytes(StandardCharsets.UTF_8));
		room = em.persist(TravelRoom.builder().title("강릉 여행").transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), ME, NOW));
		em.persist(RoomMember.joined(room.getId(), FRIEND, NOW));
		em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		recordId = em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW)).getId();
		imageId = em.persist(VisitImage.unlinked(recordId, FRIEND, "record-photo/a", NOW)).getId();
		Long otherRecord = em.persist(TravelRecord.create(room.getId() + 1000, "다른 기록", NOW)).getId();
		otherRecordImageId = em.persist(VisitImage.unlinked(otherRecord, ME, "record-photo/x", NOW)).getId();
		em.flush();
		materialStore.save(MATERIAL_ID, confirmed(ME, recordId));
	}

	/** 091 로 확정해 초안에 쓸 수 있는 자료(version 3). */
	private static ReceiptMaterial confirmed(Long uploader, Long forRecord) {
		return ReceiptMaterial.uploaded(forRecord, uploader, "receipt/u", NOW, NOW.plus(Duration.ofHours(24)))
			.linked("RECEIPT", "job-ocr", NOW)
			.ocrFailed(new ReceiptMaterial.Error("C014", "실패"))
			.reviewed(new ReceiptMaterial.Values("안목 카페", PAID_AT, LocalDate.of(2026, 11, 2), LocalTime.NOON, CAFE));
	}

	private DraftCreateCommand command(int baseVersion, List<Long> images, int materialVersion) {
		return new DraftCreateCommand(baseVersion, images, List.of(new MaterialRef(MATERIAL_ID, materialVersion)));
	}

	private DraftJobResponse create() {
		return draftService.create(ME, recordId, command(1, List.of(imageId), 3));
	}

	private RecordDraft stored(String draftKey) {
		return draftStore.find(draftKey).orElseThrow();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 접수하면_입력을_고정한_초안과_포인터를_저장하고_작업을_맡긴다() {
		executor.hold();

		DraftJobResponse response = create();

		RecordDraft draft = stored(response.draftKey());
		assertThat(response).isEqualTo(DraftJobResponse.queued(draft.draftKey(), draft.jobId()));
		assertThat(draft.status()).isEqualTo(JobStatus.QUEUED);
		assertThat(draft.requestedBy()).isEqualTo(ME);
		assertThat(draft.baseVersion()).isEqualTo(1);
		assertThat(draft.sourceSnapshot().imageIds()).containsExactly(imageId);
		assertThat(draft.sourceSnapshot().materials()).containsExactly(new RecordDraft.SnapshotMaterial(MATERIAL_ID, 3,
			confirmed(ME, recordId).values()));
		assertThat(draft.deadline()).isEqualTo(NOW.plus(Duration.ofMinutes(12)));
		assertThat(draftStore.findPointer(recordId)).contains(new RecordDraftStore.Pointer(draft.draftKey(),
			draft.jobId()));
		Job job = jobStore.find(response.jobId()).orElseThrow();
		assertThat(job.type()).isEqualTo(RecordGenerationJobPolicy.TYPE);
		assertThat(job.resourceId()).isEqualTo(recordId.toString());
		executor.reset();
	}

	@Test
	void 작업이_끝나면_영수증_항목과_근처_사진을_근거로_담고_기록은_그대로다() {
		metadataReader.put("record-photo/a", new PhotoMetadata(PAID_AT.plusSeconds(600), null, null));

		DraftJobResponse response = create();

		RecordDraft draft = stored(response.draftKey());
		assertThat(draft.status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(draft.draftVersion()).isEqualTo(1);
		assertThat(draft.warnings()).isEmpty();
		assertThat(draft.items()).singleElement().satisfies(item -> {
			assertThat(item.sourceMaterialIds()).containsExactly(MATERIAL_ID);
			assertThat(item.proposedPlace()).isEqualTo(CAFE);
			assertThat(item.sourcePhotoIds()).containsExactly(imageId);
		});
		assertThat(jobStore.find(response.jobId()).orElseThrow().status()).isEqualTo(JobStatus.SUCCEEDED);
		em.clear();
		TravelRecord record = em.find(TravelRecord.class, recordId);
		assertThat(record.getStatus()).isEqualTo(RecordStatus.DRAFT);
		assertThat(record.getVersion()).isEqualTo(1);
	}

	@Test
	void EXIF_없는_사진이면_영수증_항목만_만들고_경고를_넣는다() {
		DraftJobResponse response = create();

		RecordDraft draft = stored(response.draftKey());
		assertThat(draft.items()).singleElement().satisfies(item -> assertThat(item.sourcePhotoIds()).isEmpty());
		assertThat(draft.warnings()).containsExactly(RecordDraft.PHOTO_METADATA_MISSING);
	}

	@Test
	void 사진_읽기가_실패해도_그_사진만_빼고_만든다() {
		given(fileStorage.readPrefix(eq("photos"), anyString(), anyInt()))
			.willThrow(new BusinessException(ExceptionType.FILE_STORAGE_UNAVAILABLE));

		DraftJobResponse response = create();

		assertThat(stored(response.draftKey()).status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(stored(response.draftKey()).warnings()).containsExactly(RecordDraft.PHOTO_METADATA_MISSING);
	}

	@Test
	void 요청자가_실행_전에_나갔으면_초안과_작업이_RE003_으로_실패한다() {
		executor.hold();
		DraftJobResponse response = create();
		em.getEntityManager().createNativeQuery("update room_members set left_at = ?1 where room_id = ?2 and user_id = ?3")
			.setParameter(1, NOW).setParameter(2, room.getId()).setParameter(3, ME).executeUpdate();
		em.clear();

		executor.runHeld();

		RecordDraft draft = stored(response.draftKey());
		assertThat(draft.status()).isEqualTo(JobStatus.FAILED);
		assertThat(draft.error().code()).isEqualTo("RE003");
		assertThat(draft.items()).isEmpty();
		Job job = jobStore.find(response.jobId()).orElseThrow();
		assertThat(job.status()).isEqualTo(JobStatus.FAILED);
		assertThat(job.retryable()).isFalse();
	}

	@Test
	void 대기_제한_시간이_지나면_초안이_C014_로_실패하고_기한이_지난_진행_중_초안도_실패로_보인다() {
		executor.hold();
		DraftJobResponse response = create();
		RecordDraft queued = stored(response.draftKey());
		assertThat(queued.asOf(queued.deadline().plusSeconds(1)).error()).isEqualTo(Job.TIMED_OUT);

		clock.advance(Duration.ofMinutes(11));
		executor.runHeld();

		assertThat(stored(response.draftKey()).status()).isEqualTo(JobStatus.FAILED);
		assertThat(stored(response.draftKey()).error().code()).isEqualTo("C014");
	}

	@Test
	void 기록_권한과_baseVersion() {
		assertError(() -> draftService.create(ME, recordId + 9999, command(1, List.of(imageId), 3)),
			ExceptionType.RECORD_NOT_FOUND);
		assertError(() -> draftService.create(LEFT, recordId, command(1, List.of(imageId), 3)),
			ExceptionType.RECORD_EDIT_FORBIDDEN);
		assertError(() -> draftService.create(ME, recordId, command(2, List.of(imageId), 3)),
			ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 이_기록의_사진이_아니면_RE008() {
		assertError(() -> draftService.create(ME, recordId, command(1, List.of(imageId, otherRecordImageId), 3)),
			ExceptionType.MATERIAL_NOT_FOUND);
		assertError(() -> draftService.create(ME, recordId, command(1, List.of(imageId + 9999), 3)),
			ExceptionType.MATERIAL_NOT_FOUND);
	}

	@Test
	void 영수증은_RE008_M005_C004_RE022_순서로_본다() {
		assertError(() -> draftService.create(FRIEND, recordId, command(1, List.of(imageId), 3)),
			ExceptionType.FILE_ACCESS_DENIED);
		assertError(() -> draftService.create(ME, recordId, command(1, List.of(imageId), 2)),
			ExceptionType.VERSION_MISMATCH);

		materialStore.save(MATERIAL_ID, ReceiptMaterial.uploaded(recordId, ME, "receipt/u", NOW,
			NOW.plus(Duration.ofHours(24))).linked("RECEIPT", "job-ocr", NOW));
		assertError(() -> draftService.create(ME, recordId, command(1, List.of(imageId), 1)),
			ExceptionType.RECEIPT_NOT_READY);

		materialStore.save(MATERIAL_ID, ReceiptMaterial.uploaded(recordId, ME, "receipt/u", NOW,
			NOW.plus(Duration.ofHours(24))));
		assertError(() -> draftService.create(ME, recordId, command(1, List.of(imageId), 1)),
			ExceptionType.MATERIAL_NOT_FOUND);

		TestRedis.flushAll();
		assertError(() -> draftService.create(ME, recordId, command(1, List.of(imageId), 3)),
			ExceptionType.MATERIAL_NOT_FOUND);
	}

	@Test
	void 보정이_필요한_영수증은_RE022() {
		materialStore.save(MATERIAL_ID, ReceiptMaterial.uploaded(recordId, ME, "receipt/u", NOW,
			NOW.plus(Duration.ofHours(24))).linked("RECEIPT", "job-ocr", NOW)
			.ocrSucceeded(new ReceiptMaterial.Extracted("가게", LocalDate.of(2026, 11, 2), LocalTime.NOON, PAID_AT,
				List.of(), null)));

		assertError(() -> draftService.create(ME, recordId, command(1, List.of(imageId), 2)),
			ExceptionType.RECEIPT_NOT_READY);
	}

	@Test
	void 재전송은_권한을_다시_보고_처음과_같은_모양이다() {
		executor.hold();
		DraftJobResponse first = create();

		assertThat(draftService.created(ME, recordId, first.draftKey())).isEqualTo(first);
		assertError(() -> draftService.created(LEFT, recordId, first.draftKey()), ExceptionType.RECORD_EDIT_FORBIDDEN);
		assertError(() -> draftService.created(ME, recordId, "missing"), ExceptionType.RECORD_DRAFT_NOT_FOUND);
		executor.reset();
	}
}
