package com.ssafy.yeogirok.domain.record.service;

import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.SIGNED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.session.ReceiptMaterial;
import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.dto.ActualVisitView;
import com.ssafy.yeogirok.domain.record.dto.MaterialSummary;
import com.ssafy.yeogirok.domain.record.dto.PlannedSchedule;
import com.ssafy.yeogirok.domain.file.session.ReceiptMaterialStore;
import com.ssafy.yeogirok.domain.record.dto.RecordDetailResponse;
import com.ssafy.yeogirok.domain.record.dto.RecordPhotoView;
import com.ssafy.yeogirok.domain.record.dto.RecordProgress.Stage;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.record.entity.VisitStory;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.TestRedis;
import com.ssafy.yeogirok.support.TestUsers;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** API-059. 응답 조립(순서·사진 보관함·탈퇴자 익명화)과 권한. */
@DataJpaTest
@Import(RecordTestConfig.class)
class RecordQueryServiceTest {

	static final Long MEMBER = 5001L;
	static final Long FRIEND = 5002L;
	static final Long LEFT = 5003L;
	static final Long WITHDRAWN = 5004L;
	static final Long STRANGER = 5005L;
	static final LocalDate DAY1 = LocalDate.parse("2026-11-01");
	static final LocalDate DAY2 = LocalDate.parse("2026-11-02");
	static final Instant AT_DAY1 = Instant.parse("2026-11-01T01:00:00Z");

	@Autowired
	RecordQueryService recordQueryService;
	@Autowired
	TestEntityManager em;
	@Autowired
	FileStorage fileStorage;
	@Autowired
	ReceiptMaterialStore materialStore;
	@Autowired
	JsonMapper jsonMapper;

	TravelRoom room;
	TravelRecord record;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		TestUsers.insertActive(em.getEntityManager(), MEMBER, FRIEND, LEFT, STRANGER);
		TestUsers.insert(em.getEntityManager(), WITHDRAWN, "WITHDRAWN");
		room = em.persist(TravelRoom.builder().title("강릉 여행").startDate(DAY1).endDate(DAY2)
			.transportMode(TransportMode.CAR).createdAt(NOW).build());
		em.persist(RoomMember.creator(room.getId(), MEMBER, NOW));
		em.persist(RoomMember.joined(room.getId(), FRIEND, NOW.plusSeconds(1)));
		em.persist(RoomMember.joined(room.getId(), WITHDRAWN, NOW.plusSeconds(2)));
		em.persist(RoomMember.joined(room.getId(), LEFT, NOW)).leave(NOW);
		record = em.persist(TravelRecord.create(room.getId(), "가을 강릉", NOW));
		em.flush();
	}

	private Place place(String name) {
		return em.persist(Place.manual(name, new BigDecimal("37.7519000"), new BigDecimal("128.8761000"), null, null,
			null, NOW));
	}

	private ActualVisit visit(Place place, LocalDate date, int position, Instant visitedAt) {
		ActualVisit visit = ActualVisit.manual(record.getId(), place.getId(), date, visitedAt, NOW);
		visit.moveTo(position, NOW);
		return em.persist(visit);
	}

	private Long image(Long uploadedBy, String path) {
		return em.persist(VisitImage.unlinked(record.getId(), uploadedBy, path, NOW)).getId();
	}

	/** 사진 연결(API-068)은 아직 없어 행을 직접 바꾼다. */
	private void link(Long imageId, Long visitId, int position) {
		em.flush();
		em.getEntityManager().createNativeQuery("update visit_images set visit_id = ?1, position = ?2 where id = ?3")
			.setParameter(1, visitId).setParameter(2, position).setParameter(3, imageId).executeUpdate();
		em.clear();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 방문은_날짜와_순서대로_사진은_방문_안_순서로_보관함은_올린_순서로_주고_탈퇴자는_null이다() {
		Place sea = place("경포 해변");
		Place cafe = place("안목 카페");
		ActualVisit second = visit(cafe, DAY1, 2, Instant.parse("2026-11-01T05:00:00Z"));
		ActualVisit first = visit(sea, DAY1, 1, Instant.parse("2026-11-01T01:00:00Z"));
		ActualVisit nextDay = visit(sea, DAY2, 1, Instant.parse("2026-11-02T02:00:00Z"));
		Long poolOnly = image(MEMBER, "record-photo/a");
		Long secondInFirst = image(FRIEND, "record-photo/b");
		Long firstInFirst = image(WITHDRAWN, "record-photo/c");
		em.persist(VisitStory.write(first.getId(), WITHDRAWN, "바다가 좋았다", NOW));
		em.persist(VisitStory.write(first.getId(), FRIEND, "또 가자", NOW));
		link(secondInFirst, first.getId(), 2);
		link(firstInFirst, first.getId(), 1);

		RecordDetailResponse detail = recordQueryService.getDetail(MEMBER, record.getId());

		assertThat(detail.title()).isEqualTo("가을 강릉");
		assertThat(detail.roomId()).isEqualTo(room.getId());
		assertThat(detail.startDate()).isEqualTo(DAY1);
		assertThat(detail.endDate()).isEqualTo(DAY2);
		assertThat(detail.members()).containsExactly(new RecordDetailResponse.Member(MEMBER, "테스트", true),
			new RecordDetailResponse.Member(FRIEND, "테스트", false));
		assertThat(detail.progress().stage()).isEqualTo(Stage.EDIT);
		assertThat(detail.actualVisits()).extracting(ActualVisitView::id)
			.containsExactly(first.getId(), second.getId(), nextDay.getId());
		ActualVisitView firstView = detail.actualVisits().getFirst();
		assertThat(firstView.place().name()).isEqualTo("경포 해변");
		assertThat(firstView.order()).isEqualTo(1);
		assertThat(firstView.visitedAt()).isEqualTo(Instant.parse("2026-11-01T01:00:00Z"));
		assertThat(firstView.photos()).containsExactly(
			new RecordPhotoView(firstInFirst, SIGNED + "record-photo/c", null, null, first.getId()),
			new RecordPhotoView(secondInFirst, SIGNED + "record-photo/b", null, FRIEND, first.getId()));
		assertThat(firstView.stories()).extracting(ActualVisitView.Story::authorId).containsExactly(null, FRIEND);
		assertThat(detail.actualVisits().get(1).photos()).isEmpty();
		assertThat(detail.photoPool()).extracting(RecordPhotoView::imageId)
			.containsExactly(poolOnly, secondInFirst, firstInFirst);
		assertThat(detail.photoPool().getFirst().visitId()).isNull();
		assertThat(detail.photoPool().getFirst().url()).isEqualTo(SIGNED + "record-photo/a");
		verify(fileStorage).presignGet("photos", "record-photo/a", Duration.ofMinutes(15));
		assertThat(detail.plannedSchedule()).isNull();
		assertThat(detail.uploads()).isEmpty();
		assertThat(detail.materials()).isEmpty();
		assertThat(detail.publishedCourseId()).isNull();
		assertThat(detail.version()).isEqualTo(1);
	}

	@Test
	void 참고_계획_사본이_있으면_plannedSchedule_로_그대로_준다() {
		PlannedSchedule plan = new PlannedSchedule(room.getId(), 3, List.of(new PlannedSchedule.Day(DAY1,
			LocalTime.of(9, 0), null,
			List.of(new PlannedSchedule.Item(501L, 31L, 1, AT_DAY1, AT_DAY1.plusSeconds(3600), 60, false, null),
				new PlannedSchedule.Item(502L, 32L, 2, null, null, 30, true, null)),
			List.of(new ScheduleView.Leg(501L, 502L, TransportMode.CAR, 15, "KAKAO")),
			List.of(ScheduleView.WARNING_ARRIVAL_TIME_UNKNOWN))));
		em.find(TravelRecord.class, record.getId())
			.attachReferencePlan(jsonMapper.convertValue(plan, new TypeReference<Map<String, Object>>() {
			}));
		em.flush();
		em.clear();

		assertThat(recordQueryService.getDetail(MEMBER, record.getId()).plannedSchedule()).isEqualTo(plan);
	}

	/** FK 가 없어 지워진 장소를 가리키는 방문이 있어도 상세가 깨지지 않는다(PR #77 리뷰). */
	@Test
	void 장소_행이_없는_방문은_place_가_null이다() {
		ActualVisit lost = visit(place("사라진 장소"), DAY1, 1, AT_DAY1);
		em.flush();
		em.getEntityManager().createNativeQuery("update actual_visits set place_id = ?1 where id = ?2")
			.setParameter(1, lost.getPlaceId() + 1000).setParameter(2, lost.getId()).executeUpdate();
		em.clear();

		RecordDetailResponse detail = recordQueryService.getDetail(MEMBER, record.getId());

		assertThat(detail.actualVisits()).singleElement().satisfies(view -> {
			assertThat(view.id()).isEqualTo(lost.getId());
			assertThat(view.place()).isNull();
		});
	}

	@Test
	void materials_는_요청자_본인_자료_요약이다() {
		ReceiptMaterial failed = ReceiptMaterial.uploaded(record.getId(), MEMBER, "receipt/u", NOW,
			NOW.plus(Duration.ofHours(24))).linked("RECEIPT", "job-ocr", NOW)
			.ocrFailed(new ReceiptMaterial.Error("M003", "읽을 수 없음"));
		materialStore.save("m-1", failed);
		materialStore.addToIndex(record.getId(), MEMBER, "m-1");

		assertThat(recordQueryService.getDetail(MEMBER, record.getId()).materials()).containsExactly(
			new MaterialSummary("m-1", 2, "RECEIPT", ReceiptMaterial.Status.FAILED, ReceiptMaterial.ReviewStatus.REQUIRED,
				true, List.of("merchantName", "paidDate", "paidTime", "place"), false,
				new ReceiptMaterial.Error("M003", "읽을 수 없음")));
		assertThat(recordQueryService.getDetail(FRIEND, record.getId()).materials()).isEmpty();
	}

	@Test
	void 방문이_없으면_UPLOAD_완성했으면_COMPLETED() {
		assertThat(recordQueryService.getDetail(MEMBER, record.getId()).progress().stage()).isEqualTo(Stage.UPLOAD);

		em.find(TravelRecord.class, record.getId()).complete(NOW);
		em.flush();

		RecordDetailResponse detail = recordQueryService.getDetail(MEMBER, record.getId());
		assertThat(detail.progress().stage()).isEqualTo(Stage.COMPLETED);
		assertThat(detail.progress().draftKey()).isNull();
		assertThat(detail.actualVisits()).isEmpty();
	}

	@Test
	void 나간_사람과_비참여자는_RE002_없는_기록은_RE001() {
		assertError(() -> recordQueryService.getDetail(LEFT, record.getId()), ExceptionType.RECORD_NOT_MEMBER);
		assertError(() -> recordQueryService.getDetail(STRANGER, record.getId()), ExceptionType.RECORD_NOT_MEMBER);
		assertError(() -> recordQueryService.getDetail(MEMBER, record.getId() + 100), ExceptionType.RECORD_NOT_FOUND);
	}

	@Test
	void 방이_삭제된_기록은_RE001() {
		em.find(TravelRoom.class, room.getId()).softDelete(NOW);
		em.flush();
		em.clear();

		assertError(() -> recordQueryService.getDetail(MEMBER, record.getId()), ExceptionType.RECORD_NOT_FOUND);
	}
}
