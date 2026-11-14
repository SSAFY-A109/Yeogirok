package com.ssafy.yeogirok.domain.course.service;

import static com.ssafy.yeogirok.domain.course.CourseFixture.DAY1;
import static com.ssafy.yeogirok.domain.record.RecordTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import com.ssafy.yeogirok.domain.course.dto.ImportCountBasis;
import com.ssafy.yeogirok.domain.course.dto.MainCourseListResponse;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.record.RecordTestConfig;
import com.ssafy.yeogirok.domain.record.entity.ActualVisit;
import com.ssafy.yeogirok.domain.record.entity.TravelRecord;
import com.ssafy.yeogirok.domain.record.entity.VisitImage;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;

/**
 * 199·227 API-015 메인 추천 코스. 고르기 규칙은 MainCoursePickerTest, 30일 경계·같은 방·삭제된 방·사진 통계는
 * PublicCourseRepositoryTest, 응답 모양은 CourseControllerTest.
 */
@DataJpaTest
@Import({CourseTestConfig.class, MainCourseService.class})
class MainCourseServiceTest {

	static final long ME = 9981L;
	static final long OTHER = 9982L;

	@Autowired
	MainCourseService service;
	@Autowired
	MutableClock clock;
	@Autowired
	TestEntityManager em;

	CourseFixture fixture;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		fixture = new CourseFixture(em);
	}

	/** 방 DAY1 부터 days 일, 게시한 기록. */
	private TravelRecord course(String title, int days) {
		TravelRoom room = fixture.room(DAY1, ME);
		em.getEntityManager().createNativeQuery("UPDATE travel_rooms SET end_date = ?1 WHERE id = ?2")
			.setParameter(1, DAY1.plusDays(days - 1)).setParameter(2, room.getId()).executeUpdate();
		TravelRecord record = fixture.record(room);
		record.changeCourse(title, "", NOW, true, NOW);
		return record;
	}

	/** 방문 하나와(공개 여부를 고른) 연결 사진 하나. */
	private ActualVisit visit(TravelRecord record, String place, LocalDate date, int position, boolean publicPhoto) {
		ActualVisit visit = fixture.visit(record.getId(), fixture.place(place), date, position);
		VisitImage photo = fixture.photo(record.getId(), visit.getId(), 1);
		if (publicPhoto) {
			makePublic(photo);
		}
		return visit;
	}

	private void makePublic(VisitImage photo) {
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE visit_images SET is_public_selected = TRUE WHERE id = ?1")
			.setParameter(1, photo.getId()).executeUpdate();
	}

	/** 모든 날짜에 공개 사진이 있는 days 일짜리 코스. */
	private TravelRecord shown(String title, int days) {
		TravelRecord record = course(title, days);
		for (int day = 0; day < days; day++) {
			visit(record, title + " " + day, DAY1.plusDays(day), 1, true);
		}
		return record;
	}

	/** 새 방에 이 코스에서 가져온 후보를 at 시각에 넣는다(030 이 아직 없음). */
	private void imported(TravelRecord course, Instant at) {
		TravelRoom room = fixture.room(null, OTHER);
		RoomPlace roomPlace = em.persist(RoomPlace.manual(room.getId(),
			em.persist(Place.manual("가져온 장소", new BigDecimal("37.7"), new BigDecimal("128.9"), null, null, null, NOW)),
			OTHER, NOW));
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE room_places SET source_type = 'COURSE', source_record_id = ?1,"
				+ " created_at = ?2 WHERE id = ?3")
			.setParameter(1, course.getId()).setParameter(2, at).setParameter(3, roomPlace.getId()).executeUpdate();
	}

	private MainCourseListResponse recommend(String category, String regionCode) {
		em.flush();
		em.clear();
		return service.recommend(category, regionCode);
	}

	private MainCourseListResponse recommend() {
		return recommend(null, null);
	}

	private List<Long> ids(MainCourseListResponse response) {
		return response.items().stream().map(MainCourseListResponse.Item::id).toList();
	}

	@Test
	void 여행_모든_날짜에_공개_사진이_있는_게시_코스만_나온다() {
		TravelRecord full = shown("모든 날", 2);
		TravelRecord gap = course("하루 빔", 2);
		visit(gap, "첫날", DAY1, 1, true);
		TravelRecord privateOnly = course("비공개 사진", 1);
		visit(privateOnly, "비공개", DAY1, 1, false);
		TravelRecord unlinkedOnly = course("미연결 사진만", 1);
		fixture.visit(unlinkedOnly.getId(), fixture.place("방문"), DAY1, 1);
		fixture.photo(unlinkedOnly.getId(), null, 0);
		// 방 기간(1일) 밖 방문도 기간에 들어가 그날 사진이 있어야 한다.
		TravelRecord beyond = course("기간 밖 방문", 1);
		visit(beyond, "첫날", DAY1, 1, true);
		fixture.visit(beyond.getId(), fixture.place("다음날"), DAY1.plusDays(1), 1);
		TravelRecord unpublished = shown("해제", 1);
		unpublished.changeCourse("해제", "", null, false, NOW);
		TravelRecord deletedRoom = shown("방 삭제", 1);
		em.find(TravelRoom.class, deletedRoom.getRoomId()).softDelete(NOW);
		TravelRecord draft = shown("편집됨", 1);
		draft.markEdited(NOW);

		assertThat(ids(recommend())).containsExactlyInAnyOrder(draft.getId(), full.getId());
	}

	/** 노출 조건을 쿼리로 옮기며 기간 계산(CoursePeriod 와 같은 규칙)이 같은지 본다(PR #107 리뷰). */
	@Test
	void 기간은_방_날짜와_방문_날짜를_합쳐_세고_같은_날_사진_여러_장은_하루로_센다() {
		// 방 시작일보다 이른 방문: 그날부터 기간이라 그날과 방 날짜 모두 사진이 있어야 한다.
		TravelRecord early = course("이른 방문", 1);
		visit(early, "전날", DAY1.minusDays(1), 1, true);
		visit(early, "첫날", DAY1, 1, true);
		TravelRecord earlyGap = course("이른 방문 빔", 1);
		visit(earlyGap, "전날", DAY1.minusDays(1), 1, true);
		// 방 날짜가 없으면 방문 날짜만으로 기간을 정한다.
		TravelRecord undated = fixture.record(fixture.room(null, ME));
		undated.changeCourse("날짜 미정", "", NOW, true, NOW);
		visit(undated, "하루", DAY1, 1, true);
		visit(undated, "이틀", DAY1.plusDays(1), 1, true);
		// 같은 날 사진이 여러 장이어도 하루라 2일 여행의 둘째 날이 비면 빠진다.
		TravelRecord sameDay = course("같은 날만", 2);
		visit(sameDay, "첫 번째", DAY1, 1, true);
		visit(sameDay, "두 번째", DAY1, 2, true);

		assertThat(ids(recommend())).containsExactlyInAnyOrder(undated.getId(), early.getId());
	}

	/** 227: 가져간 방 수는 순서에 쓰지 않지만 응답 값과 기준(최근 30일, 조회 시각까지)은 그대로다. */
	@Test
	void 최근_30일_가져간_방_수와_기준은_그대로_준다() {
		TravelRecord two = shown("두 방", 1);
		TravelRecord old = shown("오래전", 1);
		imported(two, NOW.minus(Duration.ofDays(29)));
		imported(two, NOW.minus(Duration.ofDays(1)));
		imported(old, NOW.minus(Duration.ofDays(31)));

		MainCourseListResponse response = recommend();

		assertThat(response.items()).extracting(MainCourseListResponse.Item::id, MainCourseListResponse.Item::importCount30d)
			.containsExactlyInAnyOrder(Tuple.tuple(two.getId(), 2L), Tuple.tuple(old.getId(), 0L));
		assertThat(response.basis()).isEqualTo(new ImportCountBasis("IMPORT_COUNT_30D", NOW.minus(Duration.ofDays(30)), NOW));
	}

	/** 227 AC-01·07: 최대 8개, 홀수 자리는 사진 넉넉한 코스(DB 사진 통계로 판정), 다음 쪽 없음. */
	@Test
	void 최대_8개를_사진_넉넉한_코스와_나머지를_번갈아_주고_다음_쪽은_없다() {
		List<Long> rich = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) {
			rich.add(shown("사흘 " + i, 3).getId());  // 공개 사진 3장·방문 3개 → 넉넉, 3일
		}
		for (int i = 0; i < 5; i++) {
			shown("하루 " + i, 1);  // 공개 사진 1장 → 나머지
		}

		MainCourseListResponse response = recommend();

		List<Long> picked = ids(response);
		assertThat(picked).hasSize(8).doesNotHaveDuplicates();
		for (int i = 0; i < picked.size(); i++) {
			assertThat(rich.contains(picked.get(i))).as("%d번째", i + 1).isEqualTo(i % 2 == 0);
		}
		assertThat(response.nextCursor()).isNull();
	}

	/** 227: 공식 계정 코스가 많아도 사용자 코스는 자기 쪽 맨 앞 자리를 받는다(공식 여부는 방 만든 계정으로 DB 에서 판정). */
	@Test
	void 사용자_코스는_공식_코스가_많아도_앞자리를_받는다() {
		long official = em.persist(com.ssafy.yeogirok.domain.user.domain.User.signUp(
			com.ssafy.yeogirok.domain.course.OfficialAccount.PROVIDER, com.ssafy.yeogirok.domain.course.OfficialAccount.SUBJECT,
			null, "공식", null, NOW)).getId();
		for (int i = 0; i < 10; i++) {
			TravelRoom room = fixture.room(DAY1, official);
			em.getEntityManager().createNativeQuery("UPDATE travel_rooms SET end_date = start_date WHERE id = ?1")
				.setParameter(1, room.getId()).executeUpdate();
			TravelRecord record = fixture.record(room);
			record.changeCourse("공식 " + i, "", NOW, true, NOW);
			for (int position = 1; position <= 3; position++) {  // 공개 사진 3장·방문 3개 → 사진 넉넉
				visit(record, "공식 장소 " + i + "-" + position, DAY1, position, true);
			}
		}
		TravelRecord mine = shown("내 코스", 1);  // 공개 사진 1장 → 나머지 쪽이라 2번째 자리

		assertThat(ids(recommend())).hasSize(8).element(1).isEqualTo(mine.getId());
	}

	@Test
	void 카테고리와_지역으로_거르고_없는_카테고리는_CO004_지역_형식은_C003() {
		TravelRecord cafe = shown("카페", 1);
		em.flush();
		em.getEntityManager().createNativeQuery("UPDATE places SET category_code = 'CAFE', region_code = '5115010100'"
			+ " WHERE id IN (SELECT place_id FROM actual_visits WHERE record_id = ?1)").setParameter(1, cafe.getId())
			.executeUpdate();
		shown("다른", 1);

		assertThat(ids(recommend("CAFE", null))).containsExactly(cafe.getId());
		assertThat(ids(recommend(null, "51150"))).containsExactly(cafe.getId());
		assertThatThrownBy(() -> recommend("LODGING", null)).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.COURSE_RECOMMENDATION_CATEGORY_NOT_SUPPORTED);
		assertThatThrownBy(() -> recommend(null, "5")).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 미리보기는_사진_있는_방문_중_날짜별_첫_방문을_일차_순으로_3개까지_대표_사진은_첫_공개_방문_사진() {
		TravelRecord record = course("4일", 4);
		fixture.visit(record.getId(), fixture.place("사진 없는 첫 방문"), DAY1, 1);
		ActualVisit day1 = fixture.visit(record.getId(), fixture.place("첫날 카페"), DAY1, 2);
		makePublic(fixture.photo(record.getId(), day1.getId(), 2));
		VisitImage day1First = fixture.photo(record.getId(), day1.getId(), 1);
		makePublic(day1First);
		visit(record, "첫날 두 번째", DAY1, 3, true);
		ActualVisit day2 = visit(record, "둘째 날", DAY1.plusDays(1), 1, true);
		ActualVisit day3 = visit(record, "셋째 날", DAY1.plusDays(2), 1, true);
		visit(record, "넷째 날", DAY1.plusDays(3), 1, true);

		MainCourseListResponse.Item item = recommend().items().getFirst();

		assertThat(item.previewPlaces()).extracting(MainCourseListResponse.PreviewPlace::visitId,
				MainCourseListResponse.PreviewPlace::day, MainCourseListResponse.PreviewPlace::name)
			.containsExactly(Tuple.tuple(day1.getId(), 1, "첫날 카페"),
				Tuple.tuple(day2.getId(), 2, "둘째 날"),
				Tuple.tuple(day3.getId(), 3, "셋째 날"));
		// 나중에 올렸어도 사진 순서(position)가 앞선 사진이 그 방문의 대표 사진이다.
		assertThat(item.previewPlaces().getFirst().imageUrl())
			.isEqualTo(RecordTestConfig.SIGNED + day1First.getImagePath());
		assertThat(item.thumbnailUrl()).isEqualTo(RecordTestConfig.SIGNED + day1First.getImagePath());
		assertThat(List.of(item.placeCount(), item.dayCount(), item.nightCount())).containsExactly(6, 4, 3);
	}

	@Test
	void 조건에_맞는_코스가_없으면_빈_목록과_기준() {
		MainCourseListResponse response = recommend();

		assertThat(response.items()).isEmpty();
		assertThat(response.nextCursor()).isNull();
		assertThat(response.basis().periodEnd()).isEqualTo(NOW);
	}
}
