package com.ssafy.yeogirok.domain.record.draft;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.LEGAL_DONG;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.dto.ActualVisitView;
import com.ssafy.yeogirok.domain.record.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.record.dto.DraftCreateCommand.MaterialRef;
import com.ssafy.yeogirok.domain.record.dto.MaterialReviewCommand;
import com.ssafy.yeogirok.domain.record.dto.RecordDraftResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.dto.VisitAddCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.material.MaterialQueryService;
import com.ssafy.yeogirok.domain.record.material.MaterialReviewService;
import com.ssafy.yeogirok.domain.record.material.OcrResult;
import com.ssafy.yeogirok.domain.record.material.ReceiptOcrClient;
import com.ssafy.yeogirok.domain.record.material.ReceiptOcrRunner;
import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.domain.record.service.RecordQueryService;
import com.ssafy.yeogirok.domain.record.service.VisitCommandService;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.schedule.entity.ScheduleItem;
import com.ssafy.yeogirok.global.geo.PlaceKeywordSearch;
import com.ssafy.yeogirok.support.FakePlaceKeywordSearch;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;
import com.ssafy.yeogirok.support.HoldableExecutor;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;
import com.ssafy.yeogirok.support.TestUsers;

/**
 * 212 초안 합치기 흐름(specs/draft-merge.md 7절): 영수증 인식(OCR v3 주소) → 카카오 장소 후보 → 091 에서 후보 선택(customPlace) →
 * 064 초안에서 계획과 합쳐짐 → 065 → 066 반영 → 059 방문 plannedItemId. OCR·카카오는 가짜, 실제 호출은 미확인.
 */
@DataJpaTest
@Import(RecordDraftTestConfig.class)
class DraftMergeFlowTest {

	static final Long ME = 9701L;
	static final String MATERIAL_ID = "11111111-2222-4333-8444-777777777777";
	static final LocalDate DAY1 = LocalDate.parse("2026-11-07");
	static final LocalDate DAY2 = LocalDate.parse("2026-11-08");
	/** 11/7 12:00 KST. */
	static final Instant PAID_AT = Instant.parse("2026-11-07T03:00:00Z");
	/** 계획 카페(투어API 대표 좌표). 카카오 후보는 북쪽 약 80m. */
	static final BigDecimal LAT = new BigDecimal("37.7700000");
	static final BigDecimal LNG = new BigDecimal("128.9470000");
	static final BigDecimal KAKAO_LAT = new BigDecimal("37.7707000");

	@Autowired
	RecordCreateService recordCreateService;
	@Autowired
	RecordQueryService recordQueryService;
	@Autowired
	RecordDraftService draftService;
	@Autowired
	VisitCommandService visitCommandService;
	@Autowired
	MaterialQueryService materialQueryService;
	@Autowired
	MaterialReviewService reviewService;
	@Autowired
	ReceiptOcrRunner ocrRunner;
	@Autowired
	ReceiptOcrClient ocrClient;
	@Autowired
	FakePlaceKeywordSearch placeSearch;
	@Autowired
	ReceiptMaterialStore materialStore;
	@Autowired
	FileStorage fileStorage;
	@Autowired
	FixedLegalDongResolver legalDongResolver;
	@Autowired
	HoldableExecutor executor;
	@Autowired
	RecordDraftTestConfig.FakePhotoMetadataReader metadataReader;
	@Autowired
	MutableClock clock;
	@Autowired
	TestEntityManager em;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock.set(NOW);
		executor.reset();
		metadataReader.clear();
		placeSearch.reset();
		reset(ocrClient);
		legalDongResolver.willReturn(LEGAL_DONG);
		given(fileStorage.readAll(anyString(), anyString(), anyLong())).willReturn(new byte[] {1});
		given(fileStorage.readPrefix(eq("photos"), anyString(), anyInt()))
			.willAnswer(invocation -> invocation.<String>getArgument(1).getBytes(StandardCharsets.UTF_8));
		TestUsers.insertActive(em.getEntityManager(), ME);
	}

	private RoomPlace roomPlace(Long roomId, String name, BigDecimal latitude) {
		Place place = em.persist(Place.manual(name, latitude, LNG, null, null, null, NOW));
		return em.persist(RoomPlace.manual(roomId, place, ME, NOW));
	}

	private void flush() {
		em.flush();
		em.clear();
	}

	private int version(Long recordId) {
		int version = em.find(TravelRecord.class, recordId).getVersion();
		em.clear();
		return version;
	}

	@Test
	void 영수증_장소_후보를_골라_확정하면_초안에서_계획과_합쳐지고_방문에_계획이_연결된다() {
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").startDate(DAY1).endDate(DAY2)
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), ME, NOW));
		ScheduleItem cafePlan = em.persist(
			ScheduleItem.create(room.getId(), roomPlace(room.getId(), "안목 카페", LAT).getId(), DAY1, 1, 60, false));
		ScheduleItem beachPlan = em.persist(ScheduleItem.create(room.getId(),
			roomPlace(room.getId(), "경포 해변", LAT.add(new BigDecimal("0.0300000"))).getId(), DAY2, 1, 90, false));
		flush();
		Long recordId = recordCreateService.create(ME, room.getId(), null).recordId();

		// 영수증 인식: OCR v3 가 주소를 주고, 주소·상호로 카카오에서 장소 후보를 찾는다(가짜).
		given(ocrClient.recognize(any())).willReturn(new OcrResult("안목 카페", DAY1, LocalTime.NOON,
			"강원 강릉시 창해로", false));
		placeSearch.willReturn(PlaceKeywordSearch.Result.found(List.of(new PlaceKeywordSearch.Place("안목 카페 강릉점",
			KAKAO_LAT, LNG, "강원 강릉시 창해로 14", null))));
		String jobId = ocrRunner.newJobId();
		ReceiptMaterial linked = ReceiptMaterial.uploaded(recordId, ME, "receipt/u", NOW,
			NOW.plus(Duration.ofHours(24))).linked("RECEIPT", jobId, NOW);
		materialStore.save(MATERIAL_ID, linked);
		materialStore.addToIndex(recordId, ME, MATERIAL_ID);
		ocrRunner.submit(MATERIAL_ID, jobId, linked);

		// 063: 후보 하나가 MANUAL 장소로 보이고, 아직 확정 전이라 보정이 필요하다.
		var material = materialQueryService.list(ME, recordId).items().getFirst();
		assertThat(material.needsReview()).isTrue();
		RecordPlaceView candidate = material.extracted().placeCandidates().getFirst();
		assertThat(candidate).isEqualTo(new RecordPlaceView(PlaceProvider.MANUAL, null, "안목 카페 강릉점", KAKAO_LAT,
			LNG, "강원 강릉시 창해로 14", null, null, null));

		// 091: 앱(toPlaceInput)처럼 MANUAL 후보를 customPlace 로 보내 확정한다.
		reviewService.review(ME, recordId, MATERIAL_ID, new MaterialReviewCommand(material.version(), "안목 카페",
			OffsetDateTime.of(2026, 11, 7, 12, 0, 0, 0, ZoneOffset.ofHours(9)),
			new VisitPlaceInput.CustomPlace(candidate.name(), candidate.latitude(), candidate.longitude(),
				candidate.address())));
		int materialVersion = materialStore.find(MATERIAL_ID).orElseThrow().version();
		Long imageId = em.persist(VisitImage.unlinked(recordId, ME, "record-photo/a", NOW)).getId();
		flush();

		// 064·065: 계획 카페(80m)는 영수증 항목에 합쳐지고, 해변 계획은 PLAN 항목으로 남는다.
		String draftKey = draftService.create(ME, recordId, new DraftCreateCommand(version(recordId), List.of(imageId),
			List.of(new MaterialRef(MATERIAL_ID, materialVersion)))).draftKey();
		RecordDraftResponse draft = draftService.get(ME, recordId, draftKey);
		assertThat(draft.items()).extracting(item -> item.itemId(), item -> item.visitDate(),
				item -> item.sourceMaterialIds(), item -> item.plannedItemId())
			.containsExactly(tuple(1, DAY1, List.of(MATERIAL_ID), cafePlan.getId()),
				tuple(2, DAY2, List.of(), beachPlan.getId()));
		assertThat(draft.items().getFirst().evidence().sourceTypes())
			.containsExactly(RecordDraft.SourceType.RECEIPT, RecordDraft.SourceType.PLAN);
		assertThat(draft.items().getFirst().proposedPlace().name()).isEqualTo("안목 카페 강릉점");

		// 066 → 059: 합쳐진 항목을 반영하면 방문에 계획 항목 ID 가 저장된다.
		var item = draft.items().getFirst();
		Long visitId = visitCommandService.add(ME, recordId, new VisitAddCommand(version(recordId), draftKey, 1,
				List.of(new VisitAddCommand.Item(new VisitPlaceInput.CustomPlace(item.proposedPlace().name(),
					item.proposedPlace().latitude(), item.proposedPlace().longitude(), item.proposedPlace().address()),
					DAY1, item.proposedVisitedAt(), 1, item.itemId(), item.plannedItemId()))))
			.visitIds().getFirst();
		flush();
		assertThat(recordQueryService.getDetail(ME, recordId).actualVisits())
			.extracting(ActualVisitView::id, ActualVisitView::plannedItemId)
			.containsExactly(tuple(visitId, cafePlan.getId()));
	}
}
