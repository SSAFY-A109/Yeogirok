package com.ssafy.yeogirok.domain.course.repository;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;

import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.course.service.ImportWindow;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;

/** 198 가져간 방 수: 기간 [start, end), 같은 방 1회, 삭제된 대상 방 제외, 빼낸 후보 포함. 014(전체 기간)와 같은 쿼리. */
@DataJpaTest
class PublicCourseRepositoryTest {

	static final long ME = 9971L;
	static final long OTHER = 9972L;
	static final ImportWindow WINDOW = ImportWindow.endingAt(NOW);

	@Autowired
	PublicCourseRepository repository;
	@Autowired
	TestEntityManager em;

	CourseFixture fixture;
	TravelRecord course;

	@BeforeEach
	void setUp() {
		fixture = new CourseFixture(em);
		course = fixture.record(fixture.room(DAY1, ME));
		course.changeCourse("강릉", "", NOW, true, NOW);
	}

	/** 새 방에 이 코스에서 가져온 후보를 넣는다. 출처·가져간 시각은 SQL 로 채운다(030 이 아직 없음). */
	private TravelRoom imported(Instant at, boolean removed) {
		TravelRoom room = fixture.room(null, OTHER);
		imported(room, at, removed);
		return room;
	}

	private void imported(TravelRoom room, Instant at, boolean removed) {
		RoomPlace roomPlace = em.persist(RoomPlace.manual(room.getId(),
			em.persist(Place.manual("가져온 장소", new BigDecimal("37.7"), new BigDecimal("128.9"), null, null, null, NOW)),
			OTHER, NOW));
		if (removed) {
			roomPlace.remove(NOW);
		}
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE room_places SET source_type = 'COURSE', source_record_id = ?1,"
				+ " created_at = ?2 WHERE id = ?3")
			.setParameter(1, course.getId()).setParameter(2, at).setParameter(3, roomPlace.getId()).executeUpdate();
	}

	private long count(Instant from, Instant to) {
		em.flush();
		em.clear();
		return repository.findCandidates(null, null, null, null, from, to).getFirst().importCount();
	}

	@Test
	void 기간은_시작_포함_끝_미포함이고_같은_방은_한_번_삭제된_방은_빼고_빼낸_후보는_센다() {
		imported(WINDOW.start(), false);
		TravelRoom twice = imported(WINDOW.end().minusSeconds(1), false);
		imported(twice, WINDOW.start().plusSeconds(60), false);
		imported(WINDOW.start().plusSeconds(3600), true);
		imported(WINDOW.end(), false);
		imported(WINDOW.start().minusSeconds(1), false);
		TravelRoom deleted = imported(WINDOW.start().plusSeconds(7200), false);
		em.getEntityManager().createNativeQuery("UPDATE travel_rooms SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?1")
			.setParameter(1, deleted.getId()).executeUpdate();

		assertThat(count(WINDOW.start(), WINDOW.end())).isEqualTo(3);
		assertThat(count(ImportWindow.ALL.start(), ImportWindow.ALL.end())).isEqualTo(5);
	}

	/** S15P31A109-224: 작성자가 자기 코스를 원본 기록의 방으로 가져온 것은 세지 않는다(014·015 공통 조건). */
	@Test
	void 원본_기록의_방으로_가져온_후보는_세지_않는다() {
		TravelRoom origin = em.find(TravelRoom.class, course.getRoomId());
		imported(origin, WINDOW.start().plusSeconds(60), false);
		imported(WINDOW.start().plusSeconds(120), false);

		assertThat(count(WINDOW.start(), WINDOW.end())).isEqualTo(1);
	}

	/** 015 메인 추천도 같은 조건이라 원본 방 가져오기로는 정렬이 바뀌지 않는다. */
	@Test
	void 메인_추천의_가져간_방_수에도_원본_방은_들어가지_않는다() {
		TravelRoom origin = em.find(TravelRoom.class, course.getRoomId());
		Long placeId = fixture.place("경포대");
		ActualVisit visit = fixture.visit(course.getId(), placeId, DAY1, 1);
		VisitImage photo = fixture.photo(course.getId(), visit.getId(), 1);
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE travel_rooms SET end_date = start_date WHERE id = ?1")
			.setParameter(1, origin.getId()).executeUpdate();
		em.getEntityManager().createNativeQuery("UPDATE visit_images SET is_public_selected = TRUE WHERE id = ?1")
			.setParameter(1, photo.getId()).executeUpdate();
		imported(origin, WINDOW.start().plusSeconds(60), false);
		em.flush();
		em.clear();

		assertThat(repository.findMainCourses(null, null, WINDOW.start(), WINDOW.end(), Long.MAX_VALUE, Long.MAX_VALUE,
			Limit.of(10))).singleElement()
			.satisfies(candidate -> assertThat(candidate.importCount()).isZero());
	}
}
