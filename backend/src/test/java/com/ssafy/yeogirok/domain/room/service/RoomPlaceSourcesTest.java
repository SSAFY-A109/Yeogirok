package com.ssafy.yeogirok.domain.room.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.room.RoomTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceImportCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceListResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceState;
import com.ssafy.yeogirok.domain.room.dto.SourceRecordResponse;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.support.MutableClock;

/** 후보의 원본 코스(sourceRecord, API-028·022). 공개 코스 상세(API-016)와 같은 기준으로 볼 수 있을 때만 채운다. */
@DataJpaTest
@Import({RoomTestConfig.class, RoomPlaceImportService.class})
class RoomPlaceSourcesTest {

	static final long AUTHOR = 9911L;
	static final long CO_AUTHOR = 9912L;
	static final long ME = 9913L;

	@Autowired
	RoomPlaceImportService importService;
	@Autowired
	RoomPlaceService roomPlaceService;
	@Autowired
	RoomService roomService;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;

	TravelRoom sourceRoom;
	TravelRecord course;
	Long visit;
	TravelRoom target;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		CourseFixture fixture = new CourseFixture(em);
		sourceRoom = fixture.room(DAY1, AUTHOR, CO_AUTHOR);
		course = fixture.record(sourceRoom);
		visit = fixture.visit(course.getId(), fixture.place("경포대"), DAY1, 1).getId();
		course.complete(NOW);
		course.changeCourse("강릉 1박", "바다", NOW, true, NOW);
		target = fixture.room(null, ME);
		em.flush();
		importService.importPlaces(ME, target.getId(), new RoomPlaceImportCommand(course.getId(), List.of(visit)));
		// 같은 방에 코스가 아닌 후보도 하나 둔다.
		Place manual = em.persist(Place.manual("우리 숙소", new BigDecimal("37.7"),
			new BigDecimal("128.9"), null, null, null, NOW));
		em.persist(RoomPlace.manual(target.getId(), manual, ME, NOW));
		em.flush();
		em.clear();
	}

	private List<RoomPlaceListResponse.Item> places() {
		return roomPlaceService.getPlaces(ME, target.getId(), RoomPlaceState.ACTIVE).items();
	}

	@Test
	void 공개_중인_원본_코스면_제목과_작성자를_채우고_코스가_아닌_후보는_null() {
		SourceRecordResponse expected = new SourceRecordResponse(course.getId(), "강릉 1박",
			List.of(new PublicAuthor(AUTHOR, null), new PublicAuthor(CO_AUTHOR, null)));

		assertThat(places()).extracting(RoomPlaceListResponse.Item::sourceRecord).containsExactly(expected, null);
		assertThat(roomService.getRoom(ME, target.getId()).places())
			.extracting(place -> place.sourceRecord()).containsExactly(expected, null);
	}

	@Test
	void 코스_제목을_바꾸면_지금_제목을_준다() {
		em.find(TravelRecord.class, course.getId()).changeCourse("강릉 바다 1박 2일", "바다", NOW, true, NOW);
		em.flush();
		em.clear();

		assertThat(places().getFirst().sourceRecord().title()).isEqualTo("강릉 바다 1박 2일");
	}

	@Test
	void 게시를_해제하거나_원본_방이_삭제되면_null_이고_sourceRecordId_는_남는다() {
		em.find(TravelRecord.class, course.getId()).changeCourse("강릉 1박", "바다", null, false, NOW);
		em.flush();
		em.clear();
		assertThat(places().getFirst().sourceRecord()).isNull();
		assertThat(places().getFirst().sourceRecordId()).isEqualTo(course.getId());

		em.find(TravelRecord.class, course.getId()).changeCourse("강릉 1박", "바다", NOW, true, NOW);
		em.find(TravelRoom.class, sourceRoom.getId()).softDelete(NOW);
		em.flush();
		em.clear();
		assertThat(places().getFirst().sourceRecord()).isNull();
	}
}
