package com.ssafy.yeogirok.domain.room.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.room.RoomTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceImportCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceImportResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceResponse;
import com.ssafy.yeogirok.domain.room.entity.PlaceSourceType;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomMemberId;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.event.RoomChange;
import com.ssafy.yeogirok.domain.room.event.RoomChangedEvent;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;

/** API-030 공개 코스에서 가져오기. 요청 형식 오류는 RoomPlaceControllerTest 가 본다. */
@DataJpaTest
@Import({RoomTestConfig.class, RoomPlaceImportService.class})
@RecordApplicationEvents
class RoomPlaceImportServiceTest {

	static final long AUTHOR = 9901L;
	static final long ME = 9902L;
	static final long FRIEND = 9903L;
	static final long STRANGER = 9904L;

	@Autowired
	RoomPlaceImportService service;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;
	@Autowired
	ApplicationEvents events;

	CourseFixture fixture;
	TravelRoom sourceRoom;
	TravelRecord course;
	Long gyeongpo;
	Long anmok;
	Long visit1;
	Long visit2;
	TravelRoom target;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		fixture = new CourseFixture(em);
		sourceRoom = fixture.room(DAY1, AUTHOR);
		course = fixture.record(sourceRoom);
		gyeongpo = fixture.place("경포대");
		anmok = fixture.place("안목해변");
		visit1 = fixture.visit(course.getId(), gyeongpo, DAY1, 1).getId();
		visit2 = fixture.visit(course.getId(), anmok, DAY1.plusDays(1), 1).getId();
		course.complete(NOW);
		course.changeCourse("강릉 1박", "바다", NOW, true, NOW);
		target = fixture.room(null, ME, FRIEND, STRANGER);
		// STRANGER 는 나간 참여자다. 픽스처의 참여 시각(기록 테스트 시각) 뒤에 나간다.
		em.find(RoomMember.class, new RoomMemberId(target.getId(), STRANGER))
			.leave(RecordTestConfig.NOW.plusSeconds(60));
		em.flush();
		em.clear();
	}

	private RoomPlaceImportResponse importPlaces(Long user, Long recordId, Long... visitIds) {
		RoomPlaceImportResponse response = service.importPlaces(user, target.getId(),
			new RoomPlaceImportCommand(recordId, List.of(visitIds)));
		em.flush();
		em.clear();
		return response;
	}

	private int targetVersion() {
		return em.find(TravelRoom.class, target.getId()).getVersion();
	}

	private long roomPlaceCount() {
		return em.getEntityManager().createQuery("select count(rp) from RoomPlace rp where rp.roomId = :roomId",
			Long.class).setParameter("roomId", target.getId()).getSingleResult();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 고른_방문을_요청_순서대로_COURSE_후보로_만들고_version을_올린다() {
		int before = targetVersion();

		RoomPlaceImportResponse response = importPlaces(ME, course.getId(), visit2, visit1);

		assertThat(response.version()).isEqualTo(before + 1).isEqualTo(targetVersion());
		assertThat(response.added()).extracting(added -> added.place().name()).containsExactly("안목해변", "경포대");
		assertThat(response.added()).extracting(RoomPlaceResponse::sourceType, RoomPlaceResponse::sourceRecordId,
				RoomPlaceResponse::addedBy)
			.containsOnly(tuple(PlaceSourceType.COURSE, course.getId(), ME));
		RoomPlace saved = em.find(RoomPlace.class, response.added().getFirst().id());
		assertThat(saved.getRoomId()).isEqualTo(target.getId());
		assertThat(saved.getPlace().getId()).isEqualTo(anmok);
		assertThat(events.stream(RoomChangedEvent.class))
			.containsExactly(RoomChangedEvent.of(target.getId(), ME, response.version(), RoomChange.PLACES));
	}

	@Test
	void 이미_담은_장소도_다시_가져오면_새_후보가_생긴다() {
		importPlaces(ME, course.getId(), visit1);
		importPlaces(FRIEND, course.getId(), visit1);

		assertThat(roomPlaceCount()).isEqualTo(2);
	}

	@Test
	void 같은_요청을_다시_조회하면_만든_후보와_지금_version을_준다() {
		RoomPlaceImportResponse created = importPlaces(ME, course.getId(), visit1, visit2);

		RoomPlaceImportResponse replay = service.getImported(ME, target.getId(),
			RoomPlaceImportService.roomPlaceIdsOf(RoomPlaceImportService.resultIdOf(created)));

		assertThat(replay).isEqualTo(created);
	}

	@Test
	void 미게시_없는_코스_원본_방_삭제는_CO001이고_아무것도_만들지_않는다() {
		assertError(() -> importPlaces(ME, course.getId() + 1000, visit1), ExceptionType.COURSE_UNAVAILABLE);

		em.find(TravelRecord.class, course.getId()).changeCourse("강릉 1박", "바다", null, false, NOW);
		em.flush();
		assertError(() -> importPlaces(ME, course.getId(), visit1), ExceptionType.COURSE_UNAVAILABLE);

		em.find(TravelRecord.class, course.getId()).changeCourse("강릉 1박", "바다", NOW, false, NOW);
		em.find(TravelRoom.class, sourceRoom.getId()).softDelete(NOW);
		em.flush();
		assertError(() -> importPlaces(ME, course.getId(), visit1), ExceptionType.COURSE_UNAVAILABLE);

		assertThat(roomPlaceCount()).isZero();
	}

	@Test
	void 다른_코스의_방문이나_없는_방문이_하나라도_있으면_CO007이고_아무것도_만들지_않는다() {
		TravelRoom otherRoom = fixture.room(DAY1, AUTHOR);
		TravelRecord other = fixture.record(otherRoom);
		Long otherVisit = fixture.visit(other.getId(), gyeongpo, DAY1, 1).getId();
		em.flush();
		int before = targetVersion();

		assertError(() -> importPlaces(ME, course.getId(), visit1, otherVisit),
			ExceptionType.COURSE_IMPORT_VISIT_NOT_IN_COURSE);
		assertError(() -> importPlaces(ME, course.getId(), visit1, visit2 + 1000),
			ExceptionType.COURSE_IMPORT_VISIT_NOT_IN_COURSE);

		assertThat(roomPlaceCount()).isZero();
		assertThat(targetVersion()).isEqualTo(before);
	}

	@Test
	void 대상_방_참여자가_아니면_RO002_방이_삭제됐으면_RO001() {
		assertError(() -> importPlaces(STRANGER, course.getId(), visit1), ExceptionType.ROOM_NOT_MEMBER);

		em.find(TravelRoom.class, target.getId()).softDelete(NOW);
		em.flush();
		assertError(() -> importPlaces(ME, course.getId(), visit1), ExceptionType.ROOM_NOT_FOUND);
	}

	@Test
	void 내_코스를_내가_참여한_방으로도_가져올_수_있다() {
		TravelRoom authorRoom = fixture.room(null, AUTHOR);
		em.flush();

		RoomPlaceImportResponse response = service.importPlaces(AUTHOR, authorRoom.getId(),
			new RoomPlaceImportCommand(course.getId(), List.of(visit1)));

		assertThat(response.added()).hasSize(1);
	}
}
