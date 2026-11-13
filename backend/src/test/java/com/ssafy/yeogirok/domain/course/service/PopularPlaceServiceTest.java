package com.ssafy.yeogirok.domain.course.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.course.CourseTestConfig;
import com.ssafy.yeogirok.domain.course.dto.PopularPlaceListResponse;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;

/** 200 API-093 메인 인기 장소. 응답 모양은 MainControllerTest 가 본다. */
@DataJpaTest
@Import({CourseTestConfig.class, PopularPlaceService.class})
class PopularPlaceServiceTest {

	static final long ME = 9991L;
	static final Instant RECENT = NOW.minus(Duration.ofDays(1));

	@Autowired
	PopularPlaceService service;
	@Autowired
	MutableClock clock;
	@Autowired
	TestEntityManager em;

	CourseFixture fixture;
	/** 후보를 가져온 원본 코스(게시 중). */
	TravelRecord course;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		fixture = new CourseFixture(em);
		course = publishedCourse();
	}

	private TravelRecord publishedCourse() {
		TravelRecord record = fixture.record(fixture.room(DAY1, ME));
		record.changeCourse("원본 코스", "", NOW, true, NOW);
		return record;
	}

	private Place manual(String name, String latitude, String address) {
		return em.persist(Place.manual(name, new BigDecimal(latitude), new BigDecimal("128.9"), address, null, null, NOW));
	}

	/** 투어API 장소. 생성 메서드가 직접 입력뿐이라 SQL 로 출처를 바꾼다. */
	private Place tour(String providerPlaceId, String category) {
		Place place = manual("투어 " + providerPlaceId, "37.7", null);
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE places SET provider = 'TOUR_API', provider_place_id = ?1,"
				+ " category_code = ?2 WHERE id = ?3")
			.setParameter(1, providerPlaceId).setParameter(2, category).setParameter(3, place.getId()).executeUpdate();
		return place;
	}

	private TravelRoom room() {
		return fixture.room(null, ME);
	}

	/** room 이 공개 코스에서 place 를 at 시각에 가져온 후보(030 이 아직 없음). */
	private RoomPlace imported(TravelRoom room, Place place, Instant at, boolean removed) {
		return imported(course, room, place, at, removed);
	}

	private RoomPlace imported(TravelRecord from, TravelRoom room, Place place, Instant at, boolean removed) {
		RoomPlace roomPlace = em.persist(RoomPlace.manual(room.getId(), place, ME, NOW));
		if (removed) {
			roomPlace.remove(NOW);
		}
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE room_places SET source_type = 'COURSE', source_record_id = ?1,"
				+ " created_at = ?2 WHERE id = ?3")
			.setParameter(1, from.getId()).setParameter(2, at).setParameter(3, roomPlace.getId()).executeUpdate();
		return roomPlace;
	}

	private RoomPlace imported(TravelRoom room, Place place) {
		return imported(room, place, RECENT, false);
	}

	private PopularPlaceListResponse popular(String category) {
		em.flush();
		em.clear();
		return service.popular(category);
	}

	private List<Tuple> rows(PopularPlaceListResponse response) {
		return response.items().stream()
			.map(item -> Tuple.tuple(item.rank(), item.place().name(), item.importCount30d())).toList();
	}

	@Test
	void 외부_장소는_provider와_ID로_묶고_같은_방은_한_번() {
		Place tour = tour("126508", null);
		TravelRoom twice = room();
		imported(twice, tour);
		imported(twice, tour);
		imported(room(), tour);

		assertThat(rows(popular(null))).containsExactly(Tuple.tuple(1, "투어 126508", 2L));
	}

	/** S15P31A109-224: 작성자가 자기 코스를 원본 기록의 방으로 가져온 것은 인기 장소에 세지 않는다. */
	@Test
	void 원본_기록의_방으로_가져온_후보는_세지_않는다() {
		Place tour = tour("126509", null);
		imported(em.find(TravelRoom.class, course.getRoomId()), tour);
		assertThat(popular(null).items()).isEmpty();

		imported(room(), tour);
		assertThat(rows(popular(null))).containsExactly(Tuple.tuple(1, "투어 126509", 1L));
	}

	@Test
	void 직접_입력은_이름_좌표_주소가_모두_같을_때만_묶는다() {
		imported(room(), manual("우리 카페", "37.7000000", "강릉시 교동"));
		imported(room(), manual("우리 카페", "37.70", "강릉시 교동"));
		imported(room(), manual("우리 카페", "37.7", "강릉시 포남동"));
		imported(room(), manual("주소 없는 곳", "37.8", null));
		imported(room(), manual("주소 없는 곳", "37.8", null));

		assertThat(popular(null).items()).extracting(item -> item.place().name(), item -> item.place().address(),
				PopularPlaceListResponse.Item::importCount30d)
			.containsExactly(Tuple.tuple("우리 카페", "강릉시 교동", 2L), Tuple.tuple("주소 없는 곳", null, 2L),
				Tuple.tuple("우리 카페", "강릉시 포남동", 1L));
	}

	@Test
	void 표시값은_가장_최근_후보의_장소이고_같은_시각이면_후보_ID가_큰_것() {
		Place older = manual("같은 곳", "37.7", "주소");
		Place newer = manual("같은 곳", "37.7", "주소");
		Place sameTimeBigger = manual("같은 곳", "37.7", "주소");
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE places SET image_url = 'https://img/' || id").executeUpdate();
		imported(room(), older, RECENT.minusSeconds(60), false);
		imported(room(), newer, RECENT, false);
		imported(room(), sameTimeBigger, RECENT, false);

		assertThat(popular(null).items().getFirst().place().imageUrl()).isEqualTo("https://img/" + sameTimeBigger.getId());
	}

	@Test
	void 최근_30일만_삭제된_대상_방은_빼고_빼낸_후보는_센다() {
		Place place = tour("1", null);
		imported(room(), place, NOW.minus(Duration.ofDays(30)), false);
		imported(room(), place, NOW.minusSeconds(1), true);
		imported(room(), place, NOW, false);
		imported(room(), place, NOW.minus(Duration.ofDays(30)).minusSeconds(1), false);
		TravelRoom deleted = room();
		imported(deleted, place);
		em.getEntityManager().createNativeQuery("UPDATE travel_rooms SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?1")
			.setParameter(1, deleted.getId()).executeUpdate();

		assertThat(popular(null).items().getFirst().importCount30d()).isEqualTo(2);
	}

	@Test
	void 원본_코스를_공개_해제했거나_원본_방이_지워졌으면_그_코스로_가져간_것은_세지_않는다() {
		Place place = tour("1", null);
		imported(room(), place);
		TravelRecord unpublished = publishedCourse();
		imported(unpublished, room(), place, RECENT, false);
		unpublished.changeCourse("원본 코스", "", null, false, NOW);
		TravelRecord deletedOrigin = publishedCourse();
		imported(deletedOrigin, room(), place, RECENT, false);
		em.find(TravelRoom.class, deletedOrigin.getRoomId()).softDelete(NOW);
		Place onlyUnpublished = tour("2", null);
		imported(unpublished, room(), onlyUnpublished, RECENT, false);

		// 다른 공개 코스로 가져간 1곳만 남고, 공개 해제된 코스로만 가져간 장소는 나오지 않는다.
		assertThat(rows(popular(null))).containsExactly(Tuple.tuple(1, "투어 1", 1L));
	}

	@Test
	void 카테고리로_거르고_없는_카테고리는_C003() {
		imported(room(), tour("1", "CAFE"));
		imported(room(), tour("2", "NATURE"));

		assertThat(popular("CAFE").items()).extracting(item -> item.place().providerPlaceId()).containsExactly("1");
		assertThatThrownBy(() -> popular("LODGING")).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 방_수_내림차순_같으면_장소_식별값_오름차순으로_5개까지_순위를_붙인다() {
		Place top = tour("900", null);
		imported(room(), top);
		imported(room(), top);
		for (String id : List.of("500", "100", "300", "200", "400")) {
			imported(room(), tour(id, null));
		}

		PopularPlaceListResponse response = popular(null);

		assertThat(response.items()).extracting(PopularPlaceListResponse.Item::rank, item -> item.place().providerPlaceId())
			.containsExactly(Tuple.tuple(1, "900"), Tuple.tuple(2, "100"), Tuple.tuple(3, "200"), Tuple.tuple(4, "300"),
				Tuple.tuple(5, "400"));
		assertThat(response.items().getFirst().place().provider()).isEqualTo(PlaceProvider.TOUR_API);
	}

	@Test
	void 가져간_후보가_없으면_빈_목록과_기준() {
		PopularPlaceListResponse response = popular(null);

		assertThat(response.items()).isEmpty();
		assertThat(response.basis().periodEnd()).isEqualTo(NOW);
	}
}
