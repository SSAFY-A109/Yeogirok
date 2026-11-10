package com.ssafy.yeogirok.domain.record.draft;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.draft.PhotoMetadataReader.PhotoMetadata;
import com.ssafy.yeogirok.domain.record.draft.RecordDraftTestConfig.FakePhotoMetadataReader;
import com.ssafy.yeogirok.domain.record.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.dto.VisitAddCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitAddResponse;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitCreationType;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.material.ReceiptMaterials;
import com.ssafy.yeogirok.domain.record.service.VisitCommandService;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.HoldableExecutor;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;

/** API-066 AI 초안 반영(명세 specs/record-api-2.md 4-9). 요청 형식 오류는 RecordControllerTest 가 본다. */
@DataJpaTest
@Import(RecordDraftTestConfig.class)
class DraftApplyServiceTest {

	static final Long ME = 9601L;
	static final Long FRIEND = 9602L;
	static final String MATERIAL_ID = "11111111-2222-4333-8444-555555555555";
	static final LocalDate DAY = LocalDate.of(2026, 11, 2);
	static final Instant PAID_AT = Instant.parse("2026-11-02T03:00:00Z");
	static final RecordPlaceView CAFE = new RecordPlaceView(PlaceProvider.MANUAL, null, "안목 카페",
		new BigDecimal("37.7700000"), new BigDecimal("128.9470000"), null, null, null, null);
	static final VisitPlaceInput.CustomPlace CAFE_INPUT = new VisitPlaceInput.CustomPlace("안목 카페",
		new BigDecimal("37.7700000"), new BigDecimal("128.9470000"), null);

	@Autowired
	RecordDraftService draftService;
	@Autowired
	RecordDraftStore draftStore;
	@Autowired
	VisitCommandService visitCommandService;
	@Autowired
	ReceiptMaterialStore materialStore;
	@Autowired
	ReceiptMaterials materials;
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

	Long recordId;
	Long imageId;
	String draftKey;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock.set(NOW);
		executor.reset();
		metadataReader.clear();
		given(fileStorage.readPrefix(eq("photos"), anyString(), anyInt()))
			.willAnswer(invocation -> invocation.<String>getArgument(1).getBytes(StandardCharsets.UTF_8));
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").startDate(DAY).endDate(DAY)
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), ME, NOW));
		em.persist(RoomMember.joined(room.getId(), FRIEND, NOW));
		recordId = em.persist(TravelRecord.create(room.getId(), "강릉 여행", NOW)).getId();
		imageId = em.persist(VisitImage.unlinked(recordId, FRIEND, "record-photo/a", NOW)).getId();
		em.flush();
		materialStore.save(MATERIAL_ID, ReceiptMaterial.uploaded(recordId, ME, "receipt/u", NOW,
				NOW.plus(Duration.ofHours(24))).linked("RECEIPT", "job-ocr", NOW)
			.ocrFailed(new ReceiptMaterial.Error("C014", "실패"))
			.reviewed(new ReceiptMaterial.Values("안목 카페", PAID_AT, DAY, LocalTime.NOON, CAFE)));
		// 초안: 1번 영수증 항목(사진 근거 붙음)
		metadataReader.put("record-photo/a", new PhotoMetadata(PAID_AT.plusSeconds(600), null, null));
		draftKey = draftService.create(ME, recordId, new DraftCreateCommand(1, List.of(imageId),
			List.of(new DraftCreateCommand.MaterialRef(MATERIAL_ID, 3)))).draftKey();
	}

	private VisitAddCommand.Item draftItem(int itemId, int order) {
		return new VisitAddCommand.Item(CAFE_INPUT, DAY, PAID_AT, order, itemId);
	}

	private VisitAddCommand.Item manualItem(int order) {
		return new VisitAddCommand.Item(CAFE_INPUT, DAY, PAID_AT.plusSeconds(3600), order);
	}

	private VisitAddResponse apply(int version, String key, int draftVersion, VisitAddCommand.Item... items) {
		return visitCommandService.add(FRIEND, recordId, new VisitAddCommand(version, key, draftVersion, List.of(items)));
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 초안_항목은_AI_출처로_직접_추가는_USER_로_같이_저장하고_초안을_반영됨으로_표시한다() {
		VisitAddResponse response = apply(1, draftKey, 1, draftItem(1, 1), manualItem(2));

		assertThat(response.version()).isEqualTo(2);
		em.clear();
		List<ActualVisit> visits = response.visitIds().stream().map(id -> em.find(ActualVisit.class, id)).toList();
		assertThat(visits).extracting(ActualVisit::getCreationType, ActualVisit::getSourceDraftKey,
				ActualVisit::getSourceItemId)
			.containsExactly(tuple(VisitCreationType.AI, draftKey, 1), tuple(VisitCreationType.USER, null, null));
		assertThat(draftStore.find(draftKey).orElseThrow().applied()).isTrue();
	}

	@Test
	void 같은_초안을_두_번_반영하면_RE012_반영_표시를_지워도_DB_로_RE012() {
		apply(1, draftKey, 1, draftItem(1, 1));
		assertError(() -> apply(2, draftKey, 1, draftItem(1, 1)), ExceptionType.RECORD_DRAFT_ALREADY_APPLIED);

		RecordDraft applied = draftStore.find(draftKey).orElseThrow();
		draftStore.save(new RecordDraft(applied.draftKey(), applied.type(), applied.jobId(), applied.recordId(),
			applied.requestedBy(), applied.baseVersion(), applied.status(), applied.draftVersion(),
			applied.sourceSnapshot(), applied.items(), applied.warnings(), applied.error(), false, applied.createdAt(),
			applied.deadline()));

		assertError(() -> apply(2, draftKey, 1, draftItem(1, 1)), ExceptionType.RECORD_DRAFT_ALREADY_APPLIED);
	}

	@Test
	void 기록_version_과_draftVersion_이_다르면_C004() {
		assertError(() -> apply(2, draftKey, 1, draftItem(1, 1)), ExceptionType.VERSION_MISMATCH);
		assertError(() -> apply(1, draftKey, 2, draftItem(1, 1)), ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 없거나_다른_기록이거나_끝나지_않은_초안은_RE011() {
		assertError(() -> apply(1, "33333333-0000-4000-8000-000000000000", 1, draftItem(1, 1)),
			ExceptionType.RECORD_DRAFT_NOT_FOUND);

		executor.hold();
		String queued = draftService.create(ME, recordId, new DraftCreateCommand(1, List.of(imageId),
			List.of(new DraftCreateCommand.MaterialRef(MATERIAL_ID, 3)))).draftKey();
		assertError(() -> apply(1, queued, 1, draftItem(1, 1)), ExceptionType.RECORD_DRAFT_NOT_FOUND);
		executor.reset();

		RecordDraft mine = draftStore.find(draftKey).orElseThrow();
		draftStore.save(new RecordDraft(mine.draftKey(), mine.type(), mine.jobId(), recordId + 1000, mine.requestedBy(),
			mine.baseVersion(), mine.status(), mine.draftVersion(), mine.sourceSnapshot(), mine.items(), mine.warnings(),
			mine.error(), false, mine.createdAt(), mine.deadline()));
		assertError(() -> apply(1, draftKey, 1, draftItem(1, 1)), ExceptionType.RECORD_DRAFT_NOT_FOUND);
	}

	@Test
	void 초안에_없거나_두_번_보낸_itemId_는_C003() {
		assertError(() -> apply(1, draftKey, 1, draftItem(9, 1)), ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> apply(1, draftKey, 1, draftItem(1, 1), draftItem(1, 2)), ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 초안을_만든_뒤_영수증_자료가_바뀌면_C004_자료_키가_만료됐으면_통과() {
		materials.update(MATERIAL_ID, material -> material.reviewed(material.values()));
		assertError(() -> apply(1, draftKey, 1, draftItem(1, 1)), ExceptionType.VERSION_MISMATCH);

		TestRedis.template().delete("material:" + MATERIAL_ID);
		assertThat(apply(1, draftKey, 1, draftItem(1, 1)).visitIds()).hasSize(1);
	}

	@Test
	void 근거_사진이_지워졌으면_C004() {
		em.getEntityManager().createNativeQuery("delete from visit_images where id = ?1").setParameter(1, imageId)
			.executeUpdate();
		em.clear();

		assertError(() -> apply(1, draftKey, 1, draftItem(1, 1)), ExceptionType.VERSION_MISMATCH);
	}
}
