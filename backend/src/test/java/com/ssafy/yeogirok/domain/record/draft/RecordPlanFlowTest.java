package com.ssafy.yeogirok.domain.record.draft;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.LEGAL_DONG;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.dto.ActualVisitView;
import com.ssafy.yeogirok.domain.record.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.record.dto.DraftCreateCommand.MaterialRef;
import com.ssafy.yeogirok.domain.record.dto.PlannedSchedule;
import com.ssafy.yeogirok.domain.record.dto.RecordDetailResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordDraftResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordPlaceView;
import com.ssafy.yeogirok.domain.record.dto.VisitAddCommand;
import com.ssafy.yeogirok.domain.record.dto.VisitPlaceInput;
import com.ssafy.yeogirok.domain.record.dto.VisitUpdateCommand;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.service.RecordCreateService;
import com.ssafy.yeogirok.domain.record.service.RecordQueryService;
import com.ssafy.yeogirok.domain.record.service.VisitCommandService;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.schedule.entity.ScheduleItem;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;
import com.ssafy.yeogirok.support.HoldableExecutor;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;
import com.ssafy.yeogirok.support.TestUsers;

/**
 * 196 참고 계획 흐름: 057 사본 → 방 일정 변경(사본 그대로) → 064 계획 항목(같은 곳 영수증에 합쳐짐·나머지 PLAN) → 066 반영·plannedItemId
 * → 059 → 067 해제.
 * 서비스를 이어 확인한다(명세 {@code specs/record-plan.md} 3절).
 */
@DataJpaTest
@Import(RecordDraftTestConfig.class)
class RecordPlanFlowTest {

	static final Long ME = 9601L;
	static final String MATERIAL_ID = "11111111-2222-4333-8444-666666666666";
	/** 방 기간. NOW(11/9 00:30 KST) 기준 이미 끝났다. */
	static final LocalDate DAY1 = LocalDate.parse("2026-11-07");
	static final LocalDate DAY2 = LocalDate.parse("2026-11-08");
	/** 11/7 12:00 KST. */
	static final Instant PAID_AT = Instant.parse("2026-11-07T03:00:00Z");
	static final BigDecimal LAT = new BigDecimal("37.7700000");
	static final BigDecimal LNG = new BigDecimal("128.9470000");

	@Autowired
	RecordCreateService recordCreateService;
	@Autowired
	RecordQueryService recordQueryService;
	@Autowired
	RecordDraftService draftService;
	@Autowired
	VisitCommandService visitCommandService;
	@Autowired
	ReceiptMaterialStore materialStore;
	@Autowired
	FileStorage fileStorage;
	@Autowired
	FixedLegalDongResolver legalDongResolver;
	@Autowired
	HoldableExecutor executor;
	@Autowired
	MutableClock clock;
	@Autowired
	TestEntityManager em;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock.set(NOW);
		executor.reset();
		legalDongResolver.willReturn(LEGAL_DONG);
		given(fileStorage.readPrefix(eq("photos"), anyString(), anyInt()))
			.willAnswer(invocation -> invocation.<String>getArgument(1).getBytes(StandardCharsets.UTF_8));
		TestUsers.insertActive(em.getEntityManager(), ME);
	}

	private RoomPlace roomPlace(Long roomId, String name) {
		Place place = em.persist(Place.manual(name, LAT, LNG, null, null, null, NOW));
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
	void 계획_사본에서_초안_PLAN_항목을_만들고_반영하면_방문에_계획이_연결된다() {
		TravelRoom room = em.persist(TravelRoom.builder().title("강릉 여행").startDate(DAY1).endDate(DAY2)
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), ME, NOW));
		ScheduleItem cafePlan = em.persist(
			ScheduleItem.create(room.getId(), roomPlace(room.getId(), "안목 카페").getId(), DAY1, 1, 60, false));
		ScheduleItem beachPlan = em.persist(
			ScheduleItem.create(room.getId(), roomPlace(room.getId(), "경포 해변").getId(), DAY2, 1, 90, false));
		flush();

		// 057: 확정 일정을 사본으로 복사한다. 그 뒤 방 일정을 바꿔도 059 는 사본 그대로다.
		Long recordId = recordCreateService.create(ME, room.getId(), null).recordId();
		em.find(ScheduleItem.class, cafePlan.getId()).place(cafePlan.getRoomPlaceId(), DAY1, 1, 180, true);
		flush();
		PlannedSchedule planned = recordQueryService.getDetail(ME, recordId).plannedSchedule();
		assertThat(planned.days()).extracting(PlannedSchedule.Day::date).containsExactly(DAY1, DAY2);
		assertThat(planned.days().getFirst().items().getFirst().stayMinutes()).isEqualTo(60);

		// 064: 계획 카페는 같은 좌표의 영수증 항목에 합쳐지고(208), 해변 계획은 PLAN 항목으로 남는다.
		materialStore.save(MATERIAL_ID, ReceiptMaterial.uploaded(recordId, ME, "receipt/u", NOW,
				NOW.plus(Duration.ofHours(24)))
			.linked("RECEIPT", "job-ocr", NOW)
			.ocrFailed(new ReceiptMaterial.Error("C014", "실패"))
			.reviewed(new ReceiptMaterial.Values("안목 카페", PAID_AT, DAY1, LocalTime.NOON,
				new RecordPlaceView(PlaceProvider.MANUAL, null, "안목 카페", LAT,
					LNG, null, null, null, null))));
		Long imageId = em.persist(VisitImage.unlinked(recordId, ME, "record-photo/a", NOW)).getId();
		flush();
		String draftKey = draftService.create(ME, recordId,
			new DraftCreateCommand(version(recordId), List.of(imageId), List.of(new MaterialRef(MATERIAL_ID, 3))))
			.draftKey();
		RecordDraftResponse draft = draftService.get(ME, recordId, draftKey);
		assertThat(draft.items()).extracting(item -> item.itemId(), item -> item.visitDate(),
				item -> item.plannedItemId(), item -> item.proposedVisitedAt())
			.containsExactly(tuple(1, DAY1, cafePlan.getId(), PAID_AT), tuple(2, DAY2, beachPlan.getId(), null));

		// 066: 합쳐진 영수증 항목을 반영한다. 요청의 plannedItemId 가 방문에 저장된다.
		Instant visitedAt = PAID_AT;
		VisitPlaceInput cafe = new VisitPlaceInput.CustomPlace("안목 카페", LAT, LNG, null);
		Long visitId = visitCommandService.add(ME, recordId, new VisitAddCommand(version(recordId), draftKey, 1,
				List.of(new VisitAddCommand.Item(cafe, DAY1, visitedAt, 1, 1, cafePlan.getId()))))
			.visitIds().getFirst();
		flush();
		RecordDetailResponse detail = recordQueryService.getDetail(ME, recordId);
		assertThat(detail.actualVisits()).extracting(ActualVisitView::id, ActualVisitView::plannedItemId,
				ActualVisitView::visitedAt)
			.containsExactly(tuple(visitId, cafePlan.getId(), visitedAt));

		// 067: null 을 보내면 연결을 끊고 version 이 오른다.
		int before = version(recordId);
		visitCommandService.update(ME, recordId, new VisitUpdateCommand(before, List.of(
			new VisitUpdateCommand.Item(visitId, null, DAY1, visitedAt, null, true, null)), List.of(), false));
		flush();
		assertThat(recordQueryService.getDetail(ME, recordId).actualVisits()).extracting(ActualVisitView::plannedItemId)
			.containsOnlyNulls();
		assertThat(version(recordId)).isEqualTo(before + 1);
	}
}
