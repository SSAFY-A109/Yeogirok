package com.ssafy.yeogirok.domain.course.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.course.CourseTestConfig;
import com.ssafy.yeogirok.domain.course.dto.MainCourseListResponse;
import com.ssafy.yeogirok.domain.course.dto.PopularPlaceListResponse;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.support.MutableClock;

/**
 * 201 메인 흐름: 공개 코스의 장소를 여행방이 가져가면 015·093 에 잡히고, 30일이 지나거나 대상 방이 지워지면 빠지며, 공개를
 * 해제하면 015 노출에서도 빠진다. 030 이 아직 없어 가져온 후보는 직접 넣는다(코스 방문과 같은 place_id).
 */
@DataJpaTest
@Import({CourseTestConfig.class, MainCourseService.class, PopularPlaceService.class})
class MainApiFlowTest {

	static final long ME = 9961L;
	static final long OTHER = 9962L;

	@Autowired
	MainCourseService mainCourseService;
	@Autowired
	PopularPlaceService popularPlaceService;
	@Autowired
	MutableClock clock;
	@Autowired
	TestEntityManager em;

	private MainCourseListResponse courses() {
		em.flush();
		em.clear();
		return mainCourseService.recommend(null, null, null, null);
	}

	private PopularPlaceListResponse places() {
		em.flush();
		em.clear();
		return popularPlaceService.popular(null);
	}

	@Test
	void 가져가면_015_093에_잡히고_30일이_지나거나_대상_방이_지워지면_빠지고_공개를_해제하면_015에서도_빠진다() {
		clock.set(NOW);
		CourseFixture fixture = new CourseFixture(em);
		TravelRoom origin = fixture.room(DAY1, ME);
		em.getEntityManager().createNativeQuery("UPDATE travel_rooms SET end_date = start_date WHERE id = ?1")
			.setParameter(1, origin.getId()).executeUpdate();
		TravelRecord course = fixture.record(origin);
		Long placeId = fixture.place("경포대");
		ActualVisit visit = fixture.visit(course.getId(), placeId, DAY1, 1);
		VisitImage photo = fixture.photo(course.getId(), visit.getId(), 1);
		course.changeCourse("강릉 당일", "", NOW, true, NOW);
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE visit_images SET is_public_selected = TRUE WHERE id = ?1")
			.setParameter(1, photo.getId()).executeUpdate();

		// 다른 방이 이 코스의 장소를 하루 전에 가져갔다.
		TravelRoom target = fixture.room(null, OTHER);
		RoomPlace imported = em.persist(RoomPlace.manual(target.getId(), em.find(Place.class, placeId), OTHER, NOW));
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE room_places SET source_type = 'COURSE', source_record_id = ?1,"
				+ " created_at = ?2 WHERE id = ?3")
			.setParameter(1, course.getId()).setParameter(2, NOW.minus(Duration.ofDays(1)))
			.setParameter(3, imported.getId()).executeUpdate();

		assertThat(courses().items()).extracting(MainCourseListResponse.Item::id, MainCourseListResponse.Item::importCount30d)
			.containsExactly(Tuple.tuple(course.getId(), 1L));
		assertThat(places().items()).extracting(item -> item.place().name(), PopularPlaceListResponse.Item::importCount30d)
			.containsExactly(Tuple.tuple("경포대", 1L));

		// 30일이 지나면 집계에서 빠진다(코스는 노출 조건을 만족해 0으로 남는다).
		clock.advance(Duration.ofDays(30));
		assertThat(courses().items().getFirst().importCount30d()).isZero();
		assertThat(places().items()).isEmpty();

		// 기간 안이어도 대상 방이 지워지면 빠진다.
		clock.set(NOW);
		em.getEntityManager().createNativeQuery("UPDATE travel_rooms SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?1")
			.setParameter(1, target.getId()).executeUpdate();
		assertThat(courses().items().getFirst().importCount30d()).isZero();
		assertThat(places().items()).isEmpty();

		// 공개를 해제하면 015 노출 대상이 아니다.
		em.find(TravelRecord.class, course.getId()).changeCourse("강릉 당일", "", null, false, NOW);
		assertThat(courses().items()).isEmpty();
	}
}
