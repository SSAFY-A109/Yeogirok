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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.file.storage.FileStorage;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.dto.ActualVisitView;
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
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.TestUsers;

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

	@Autowired
	RecordQueryService recordQueryService;
	@Autowired
	TestEntityManager em;
	@Autowired
	FileStorage fileStorage;

	TravelRoom room;
	TravelRecord record;

	@BeforeEach
	void setUp() {
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
