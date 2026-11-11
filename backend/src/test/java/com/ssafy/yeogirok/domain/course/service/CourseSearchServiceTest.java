package com.ssafy.yeogirok.domain.course.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.course.CourseFixture;
import com.ssafy.yeogirok.domain.course.CourseTestConfig;
import com.ssafy.yeogirok.domain.course.dto.CourseSearchCondition;
import com.ssafy.yeogirok.domain.course.dto.CourseSearchResponse;
import com.ssafy.yeogirok.domain.course.dto.PublicAuthor;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.RoomMember;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.support.TestUsers;

/** 188 API-014 공개 코스 검색. 조건 값 오류는 CourseSearchConditionTest, 응답 모양은 CourseControllerTest 가 본다. */
@DataJpaTest
@Import({CourseTestConfig.class, CourseSearchService.class})
class CourseSearchServiceTest {

	static final long ME = 9901L;
	static final long FRIEND = 9902L;
	static final CourseSearchCondition ALL = CourseSearchCondition.of(null, null, null, null, null, null);

	@Autowired
	CourseSearchService service;
	@Autowired
	TestEntityManager em;

	CourseFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = new CourseFixture(em);
	}

	/** 방(시작 DAY1, 기간 days 일)과 게시한 기록, 그 방문 장소 이름들. */
	private TravelRecord course(String title, int days, TransportMode mode, String... placeNames) {
		TravelRoom room = fixture.room(DAY1, ME);
		em.getEntityManager().createNativeQuery("UPDATE travel_rooms SET end_date = ?1, transport_mode = ?2 WHERE id = ?3")
			.setParameter(1, DAY1.plusDays(days - 1)).setParameter(2, mode.name()).setParameter(3, room.getId())
			.executeUpdate();
		TravelRecord record = fixture.record(room);
		for (int i = 0; i < placeNames.length; i++) {
			fixture.visit(record.getId(), fixture.place(placeNames[i]), DAY1, i + 1);
		}
		record.changeCourse(title, "", NOW, true, NOW);
		return record;
	}

	private TravelRecord course(String title, String... placeNames) {
		return course(title, 1, TransportMode.CAR, placeNames);
	}

	private void placeInfo(TravelRecord record, String category, String regionCode, String address, String imageUrl) {
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE places SET category_code = ?1, region_code = ?2, address = ?3,"
				+ " image_url = ?4 WHERE id IN (SELECT place_id FROM actual_visits WHERE record_id = ?5)")
			.setParameter(1, category).setParameter(2, regionCode).setParameter(3, address).setParameter(4, imageUrl)
			.setParameter(5, record.getId()).executeUpdate();
	}

	/** room 에 이 코스에서 가져온 후보를 하나 넣는다. 출처 칸은 SQL 로 채워 엔티티를 더 바꾸지 않는다. */
	private void imported(TravelRoom room, TravelRecord course, boolean removed) {
		RoomPlace roomPlace = em.persist(RoomPlace.manual(room.getId(),
			em.persist(Place.manual("가져온 장소", new BigDecimal("37.7"), new BigDecimal("128.9"), null, null, null, NOW)),
			ME, NOW));
		if (removed) {
			roomPlace.remove(NOW);
		}
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE room_places SET source_type = 'COURSE', source_record_id = ?1"
			+ " WHERE id = ?2").setParameter(1, course.getId()).setParameter(2, roomPlace.getId()).executeUpdate();
	}

	private CourseSearchResponse search(CourseSearchCondition condition, String cursor, Integer limit) {
		em.flush();
		em.clear();
		return service.search(condition, cursor, limit);
	}

	private List<Long> ids(CourseSearchCondition condition) {
		return search(condition, null, null).items().stream().map(CourseSearchResponse.Item::id).toList();
	}

	private static CourseSearchCondition where(String q, String regionCode, String category, String duration,
		String transportMode) {
		return CourseSearchCondition.of(q, regionCode, category, null, duration, transportMode);
	}

	@Test
	void 가져간_방_수가_많은_순_같으면_최근_순이고_같은_방은_한_번_삭제된_방은_빼고_빼낸_후보는_센다() {
		TravelRecord none = course("가져간 방 없음", "경포대");
		TravelRecord two = course("두 방", "경포대");
		TravelRecord one = course("한 방", "경포대");
		TravelRecord alsoNone = course("가져간 방 없음 2", "경포대");
		TravelRoom first = fixture.room(null, FRIEND);
		imported(first, two, false);
		imported(first, two, false);
		imported(fixture.room(null, FRIEND), two, true);
		imported(fixture.room(null, FRIEND), one, false);
		TravelRoom deleted = fixture.room(null, FRIEND);
		imported(deleted, one, false);
		imported(deleted, none, false);
		em.getEntityManager().createNativeQuery("UPDATE travel_rooms SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?1")
			.setParameter(1, deleted.getId()).executeUpdate();

		CourseSearchResponse response = search(ALL, null, null);

		assertThat(response.items()).extracting(CourseSearchResponse.Item::id, CourseSearchResponse.Item::importCount)
			.containsExactly(Tuple.tuple(two.getId(), 2L),
				Tuple.tuple(one.getId(), 1L),
				Tuple.tuple(alsoNone.getId(), 0L),
				Tuple.tuple(none.getId(), 0L));
		assertThat(response.totalCount()).isEqualTo(4);
	}

	@Test
	void 미게시와_방_삭제된_코스는_빼고_편집으로_DRAFT가_된_게시_코스는_넣는다() {
		TravelRecord shown = course("보임", "경포대");
		course("해제", "경포대").changeCourse("해제", "", null, false, NOW);
		TravelRecord deleted = course("방 삭제", "경포대");
		em.find(TravelRoom.class, deleted.getRoomId()).softDelete(NOW);
		TravelRecord draft = course("편집됨", "경포대");
		draft.markEdited(NOW);

		assertThat(ids(ALL)).containsExactly(draft.getId(), shown.getId());
	}

	@Test
	void 검색어는_제목이나_방문_장소_이름에_대소문자_없이_포함되고_특수문자는_글자_그대로다() {
		TravelRecord byTitle = course("Gangneung 바다", "경포대");
		TravelRecord byPlace = course("1박 2일", "안목 Coffee 거리");
		course("100% 만족", "속초");

		assertThat(ids(where("gangneung", null, null, null, null))).containsExactly(byTitle.getId());
		assertThat(ids(where(" coffee ", null, null, null, null))).containsExactly(byPlace.getId());
		assertThat(ids(where("%", null, null, null, null))).hasSize(1);
		assertThat(ids(where("_", null, null, null, null))).isEmpty();
	}

	@Test
	void 지역은_방문_장소의_법정동_코드_앞자리_카테고리는_방문_장소_카테고리() {
		TravelRecord gangneung = course("강릉", "경포대");
		placeInfo(gangneung, "NATURE", "5115010100", null, null);
		TravelRecord sokcho = course("속초", "영금정");
		placeInfo(sokcho, "CAFE", "5121010100", null, null);
		TravelRecord busan = course("부산", "해운대");
		placeInfo(busan, "NATURE", "2635010100", null, null);

		assertThat(ids(where(null, "51", null, null, null))).containsExactly(sokcho.getId(), gangneung.getId());
		assertThat(ids(where(null, "51150", null, null, null))).containsExactly(gangneung.getId());
		assertThat(ids(where(null, null, "NATURE", null, null))).containsExactly(busan.getId(), gangneung.getId());
		assertThat(ids(where(null, "51", "NATURE", null, null))).containsExactly(gangneung.getId());
	}

	@Test
	void 기간은_dayCount_로_당일_1박_2박_이상을_나누고_이동_수단으로_거른다() {
		TravelRecord dayTrip = course("당일", 1, TransportMode.CAR, "경포대");
		TravelRecord oneNight = course("1박", 2, TransportMode.TRANSIT, "경포대");
		TravelRecord twoNights = course("2박", 3, TransportMode.CAR, "경포대");
		TravelRecord threeNights = course("3박", 4, TransportMode.CAR, "경포대");

		assertThat(ids(where(null, null, null, "DAY_TRIP", null))).containsExactly(dayTrip.getId());
		assertThat(ids(where(null, null, null, "ONE_NIGHT_TWO_DAYS", null))).containsExactly(oneNight.getId());
		assertThat(ids(where(null, null, null, "TWO_NIGHTS_OR_MORE", null)))
			.containsExactly(threeNights.getId(), twoNights.getId());
		assertThat(ids(where(null, null, null, "ALL", "TRANSIT"))).containsExactly(oneNight.getId());
	}

	@Test
	void 한_묶음보다_많으면_커서로_이어_받고_전체_개수는_그대로다() {
		TravelRecord a = course("a", "경포대");
		TravelRecord b = course("b", "경포대");
		TravelRecord c = course("c", "경포대");

		CourseSearchResponse page1 = search(ALL, null, 2);
		CourseSearchResponse page2 = search(ALL, page1.nextCursor(), 2);

		assertThat(page1.items()).extracting(CourseSearchResponse.Item::id).containsExactly(c.getId(), b.getId());
		assertThat(page2.items()).extracting(CourseSearchResponse.Item::id).containsExactly(a.getId());
		assertThat(List.of(page1.totalCount(), page2.totalCount())).containsExactly(3, 3);
		assertThat(page2.nextCursor()).isNull();
		assertThat(search(ALL, null, 3).nextCursor()).isNull();
	}

	@Test
	void 카드에는_시군구_대표_사진_방문_수_경로_5개_작성자가_들어간다() {
		TravelRecord record = course("강릉 1박", 2, TransportMode.CAR, "경포대", "안목해변", "오죽헌", "초당", "주문진", "강문");
		placeInfo(record, "NATURE", "5115010100", "강원특별자치도 강릉시 창해로 365", "https://tour/img.jpg");
		em.persist(RoomMember.joined(record.getRoomId(), FRIEND, NOW));
		TestUsers.insertActive(em.getEntityManager(), FRIEND);

		CourseSearchResponse.Item item = search(ALL, null, null).items().getFirst();

		assertThat(item.regionName()).isEqualTo("강릉시");
		assertThat(item.thumbnailUrl()).isEqualTo("https://tour/img.jpg");
		assertThat(item.placeCount()).isEqualTo(6);
		assertThat(List.of(item.dayCount(), item.nightCount())).containsExactly(2, 1);
		assertThat(item.routePreview()).containsExactly("경포대", "안목해변", "오죽헌", "초당", "주문진");
		assertThat(item.authors()).containsExactly(new PublicAuthor(ME, null), new PublicAuthor(FRIEND, null));
		assertThat(item.transportMode()).isEqualTo(TransportMode.CAR);
	}

	@Test
	void 대표_사진은_방문_순서대로_첫_공개_사진이고_비공개_사진은_쓰지_않는다() {
		TravelRecord record = course("사진", "경포대", "안목해변");
		List<ActualVisit> visits = em.getEntityManager()
			.createQuery("select v from ActualVisit v where v.recordId = :id order by v.position", ActualVisit.class)
			.setParameter("id", record.getId()).getResultList();
		fixture.photo(record.getId(), visits.get(0).getId(), 1);
		VisitImage second = fixture.photo(record.getId(), visits.get(1).getId(), 1);
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE visit_images SET is_public_selected = TRUE WHERE id = ?1")
			.setParameter(1, second.getId()).executeUpdate();

		assertThat(search(ALL, null, null).items().getFirst().thumbnailUrl())
			.isEqualTo(RecordTestConfig.SIGNED + second.getImagePath());
	}

	@Test
	void 주소가_없으면_시도_이름_둘_다_없으면_빈_문자열() {
		TravelRecord noAddress = course("세종 없음", "어딘가");
		placeInfo(noAddress, null, "5011010100", null, null);
		placeInfo(course("아무 정보 없음", "어딘가 2"), null, null, null, null);

		assertThat(search(ALL, null, null).items()).extracting(CourseSearchResponse.Item::regionName)
			.containsExactly("", "제주특별자치도");
	}
}
