package com.ssafy.yeogirok.domain.course.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.course.CourseTestConfig;
import com.ssafy.yeogirok.domain.course.dto.CourseDetailResponse;
import com.ssafy.yeogirok.domain.course.dto.CourseSearchCondition;
import com.ssafy.yeogirok.domain.course.dto.MainCourseListResponse;
import com.ssafy.yeogirok.domain.course.service.CourseDetailService;
import com.ssafy.yeogirok.domain.course.service.CoursePreviewService;
import com.ssafy.yeogirok.domain.course.service.CoursePublishService;
import com.ssafy.yeogirok.domain.course.service.CourseSearchService;
import com.ssafy.yeogirok.domain.course.service.MainCourseService;
import com.ssafy.yeogirok.domain.place.service.TourPlaces;
import com.ssafy.yeogirok.domain.user.domain.User;
import com.ssafy.yeogirok.domain.user.repository.UserRepository;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.oauth.OAuthProvider;
import com.ssafy.yeogirok.support.InMemoryFileStorage;

import tools.jackson.databind.json.JsonMapper;

/**
 * 묶음 파일 → DB → 015·014·016 흐름(명세 tour-course-seed.md 6-4~6-6, AC-01·04~08·10~14). 코스마다 실제로 커밋·되돌리기를
 * 하므로 테스트 트랜잭션을 쓰지 않고, 만든 행은 끝나고 지운다. 값은 가짜다.
 */
@DataJpaTest
@Import({CourseTestConfig.class, CoursePreviewService.class, CoursePublishService.class, CourseDetailService.class,
	CourseSearchService.class, MainCourseService.class, CourseSeedJob.class, CourseSeeder.class, SeedOwner.class,
	SeedPlaces.class, SeedPhotos.class, TourPlaces.class, CourseSeedFlowTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CourseSeedFlowTest {

	static final byte[] JPEG = {(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1};
	static final String ALL_ROOMS = "tourapi-course:%";
	static final String JPEG_SHA256 = SeedPhotos.sha256(JPEG);

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		InMemoryFileStorage inMemoryFileStorage() {
			return new InMemoryFileStorage();
		}
	}

	@Autowired
	CourseSeedJob job;
	@Autowired
	MainCourseService mainCourseService;
	@Autowired
	CourseSearchService searchService;
	@Autowired
	CourseDetailService detailService;
	@Autowired
	InMemoryFileStorage storage;
	@Autowired
	UserRepository userRepository;
	@Autowired
	DataSource dataSource;
	@Autowired
	JsonMapper jsonMapper;

	@TempDir
	Path dir;
	JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
	}

	@AfterEach
	void tearDown() {
		for (String table : List.of("visit_stories", "visit_images", "actual_visits", "travel_records", "room_places",
			"room_members", "travel_rooms", "places", "users")) {
			jdbc.update("DELETE FROM " + table);
		}
		storage.keys("photos").forEach(key -> storage.delete("photos", key));
	}

	// 코스 만들기 도우미
	static Map<String, Object> place(String id, int day, int order, String date, String time, String photo) {
		Map<String, Object> place = new LinkedHashMap<>();
		place.put("contentId", id);
		place.put("name", "장소" + id);
		place.put("overview", "장소" + id + " 설명");
		place.put("day", day);
		place.put("order", order);
		place.put("visitDate", date);
		place.put("visitTime", time);
		place.put("mapx", "128.87");
		place.put("mapy", "37.75");
		place.put("address", "강원특별자치도 강릉시 가짜로 " + id);
		place.put("regionCode", "5115010100");
		place.put("regionName", "가짜동");
		place.put("lclsSystm2", "NA01");
		place.put("lclsSystm3", "NA010100");
		place.put("imageUrl", photo == null ? "" : "https://tong.example.test/" + id + ".jpg");
		place.put("cpyrhtDivCd", photo == null ? "" : "Type1");
		place.put("photo", photo);
		place.put("photoSha256", photo == null ? null : CourseSeedFlowTest.JPEG_SHA256);
		return place;
	}

	static Map<String, Object> course(String id, String title, List<Map<String, Object>> places) {
		Map<String, Object> course = new LinkedHashMap<>();
		course.put("contentId", id);
		course.put("title", title);
		course.put("overview", title + " 소개 글입니다.");
		course.put("days", places.stream().mapToInt(p -> (Integer)p.get("day")).max().orElse(1));
		course.put("places", places);
		return course;
	}

	/** 2일 코스: 날마다 사진 장소 1곳. */
	static Map<String, Object> twoDays(String title) {
		return course("c-two", title, List.of(
			place("p1", 1, 1, "2022-03-04", "09:00", "photos/p1.jpg"),
			place("p2", 1, 2, "2022-03-04", "10:00", null),
			place("p3", 2, 1, "2022-03-05", "09:00", "photos/p3.jpg"),
			place("p4", 2, 2, "2022-03-05", "10:00", null)));
	}

	static Map<String, Object> oneDay() {
		return course("c-one", "당일 코스", List.of(
			place("p5", 1, 1, "2023-05-01", "09:00", "photos/p5.jpg"),
			place("p6", 1, 2, "2023-05-01", "10:00", null),
			place("p7", 1, 3, "2023-05-01", "11:00", null)));
	}

	/** 스크립트(tour_bundle.py)가 만드는 모양 그대로: createdAt·source 처럼 적재가 안 쓰는 칸도 있다. */
	void bundle(String bundleId, List<Map<String, Object>> courses) throws IOException {
		Map<String, Object> bundle = new LinkedHashMap<>();
		bundle.put("bundleId", bundleId);
		bundle.put("createdAt", "2026-11-14T10:00:00+09:00");
		bundle.put("source", "tour-course-fetch");
		bundle.put("courses", courses);
		Files.write(dir.resolve("bundle-" + bundleId + ".json"), jsonMapper.writeValueAsBytes(bundle));
		Files.createDirectories(dir.resolve("photos"));
		for (String id : List.of("p1", "p3", "p5")) {
			Files.write(dir.resolve("photos/" + id + ".jpg"), JPEG);
		}
	}

	CourseSeedJob.Report run(Long owner, List<String> unpublish, List<String> reseed) {
		return job.run(new CourseSeedProperties(true, CourseSeedProperties.Source.LOCAL, dir.toString(), owner,
			unpublish, reseed));
	}

	CourseSeedJob.Report run() {
		return run(null, List.of(), List.of());
	}

	Long recordOf(String contentId) {
		return jdbc.queryForObject("SELECT r.id FROM travel_records r JOIN travel_rooms m ON m.id = r.room_id"
			+ " WHERE m.deleted_at IS NULL AND m.title LIKE ?", Long.class, "tourapi-course:" + contentId + ":%");
	}

	long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	@Test
	void 넣은_코스가_015_014_016_에_나오고_출처가_보이며_일수가_일정과_맞는다() throws IOException {
		bundle("b1", List.of(twoDays("강릉 1박2일"), oneDay()));

		CourseSeedJob.Report report = run();

		assertThat(report.inserted()).isEqualTo(2);
		assertThat(report.failed()).isEmpty();
		Long twoDaysId = recordOf("c-two");
		// 015: 큰 사진(첫 공개 방문 사진), 날짜별 미리보기, 장소 수, 일수(AC-04·11)
		MainCourseListResponse.Item main = mainCourseService.recommend(null, null, null, null).items().stream()
			.filter(item -> item.id().equals(twoDaysId)).findFirst().orElseThrow();
		assertThat(main.thumbnailUrl()).startsWith(InMemoryFileStorage.SIGNED + "record-photo/");
		assertThat(main.previewPlaces()).extracting(MainCourseListResponse.PreviewPlace::day,
			MainCourseListResponse.PreviewPlace::name).containsExactly(
			tuple(1, "장소p1"), tuple(2, "장소p3"));
		assertThat(main.placeCount()).isEqualTo(4);
		assertThat(List.of(main.dayCount(), main.nightCount())).containsExactly(2, 1);
		// 014 검색(AC-05)
		assertThat(searchService.search(CourseSearchCondition.of(null, null, null, null, null, null), null, null)
			.items()).extracting(item -> item.title()).contains("강릉 1박2일", "당일 코스");
		// 016 상세: 소개 글·장소 사진 출처(AC-05·06), 서버가 올린 사진을 기존 조회 URL 로(AC-01)
		CourseDetailResponse detail = detailService.getDetail(twoDaysId);
		assertThat(detail.description()).isEqualTo("강릉 1박2일 소개 글입니다.\n\n출처: 한국관광공사(투어API)");
		assertThat(List.of(detail.dayCount(), detail.nightCount())).containsExactly(2, 1);
		assertThat(detail.days()).hasSize(2);
		var firstDay = detail.days().getFirst().items();
		assertThat(firstDay.getFirst().place().imageAttribution()).isEqualTo("한국관광공사");
		assertThat(firstDay.get(1).place().imageAttribution()).isNull();
		assertThat(firstDay.getFirst().photos()).singleElement()
			.satisfies(photo -> assertThat(photo.url()).startsWith(InMemoryFileStorage.SIGNED + "record-photo/"));
		String key = firstDay.getFirst().photos().getFirst().url().substring(InMemoryFileStorage.SIGNED.length());
		assertThat(storage.get("photos", key)).hasValueSatisfying(stored -> assertThat(stored.bytes()).isEqualTo(JPEG));
		// 이야기는 장소 설명, 후보 장소는 만들지 않는다(AC-10)
		assertThat(count("SELECT COUNT(*) FROM visit_stories")).isEqualTo(7);
		assertThat(count("SELECT COUNT(*) FROM room_places")).isZero();
		assertThat(count("SELECT COUNT(*) FROM travel_rooms WHERE transport_mode = 'CAR' AND title LIKE ?", ALL_ROOMS))
			.isEqualTo(2);
	}

	@Test
	void 같은_파일로_두_번_돌려도_코스_장소_계정이_늘지_않고_시스템_계정은_하나다() throws IOException {
		bundle("b1", List.of(twoDays("강릉 1박2일"), oneDay()));
		run();
		long rooms = count("SELECT COUNT(*) FROM travel_rooms");
		long places = count("SELECT COUNT(*) FROM places");
		long photos = storage.keys("photos").size();

		CourseSeedJob.Report again = run();

		assertThat(List.of(again.inserted(), again.skipped())).containsExactly(0, 2);
		assertThat(count("SELECT COUNT(*) FROM travel_rooms")).isEqualTo(rooms);
		assertThat(count("SELECT COUNT(*) FROM places")).isEqualTo(places);
		assertThat(storage.keys("photos")).hasSize((int)photos);
		assertThat(count("SELECT COUNT(*) FROM users WHERE provider = 'KAKAO' AND provider_subject = ?",
			SeedOwner.SYSTEM_SUBJECT)).isEqualTo(1);
	}

	@Test
	void 한_코스가_중간에_실패하면_그_코스의_행과_사진_파일이_남지_않고_다른_코스는_들어간다() throws IOException {
		// 여행 날짜가 오늘 이후라 기록 생성(RE024)에서 실패한다 — 사진을 올리고 방을 저장한 뒤의 실패.
		Map<String, Object> future = course("c-future", "미래 코스", List.of(
			place("p1", 1, 1, "2099-01-01", "09:00", "photos/p1.jpg"),
			place("p2", 1, 2, "2099-01-01", "10:00", null),
			place("p3", 1, 3, "2099-01-01", "11:00", "photos/p3.jpg")));
		bundle("b1", List.of(future, oneDay()));

		CourseSeedJob.Report report = run();

		assertThat(report.inserted()).isEqualTo(1);
		assertThat(report.failed()).containsOnlyKeys("c-future");
		assertThat(count("SELECT COUNT(*) FROM travel_rooms WHERE title LIKE 'tourapi-course:c-future:%'")).isZero();
		assertThat(count("SELECT COUNT(*) FROM travel_records")).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM actual_visits")).isEqualTo(3);
		assertThat(count("SELECT COUNT(*) FROM visit_images")).isEqualTo(1);
		assertThat(storage.keys("photos")).hasSize(1);  // 당일 코스 사진 하나만
	}

	@Test
	void 사진을_못_읽어_어떤_날의_사진이_0장이면_그_코스는_넣지_않는다() throws IOException {
		bundle("b1", List.of(twoDays("강릉 1박2일")));
		Files.delete(dir.resolve("photos/p3.jpg"));

		CourseSeedJob.Report report = run();

		assertThat(report.failed()).containsEntry("c-two", "PHOTO_MISSING_DAY");
		assertThat(count("SELECT COUNT(*) FROM travel_rooms")).isZero();
		assertThat(storage.keys("photos")).isEmpty();
	}

	@Test
	void 주인_설정이_있으면_그_회원에게_넣고_탈퇴_회원이면_아무것도_넣지_않는다() throws IOException {
		bundle("b1", List.of(oneDay()));
		User member = userRepository.save(User.signUp(OAuthProvider.GOOGLE, "google-sub", null, "운영", null,
			java.time.Instant.parse("2026-11-01T00:00:00Z")));
		User withdrawn = User.signUp(OAuthProvider.GOOGLE, "google-sub-2", null, "탈퇴", null,
			java.time.Instant.parse("2026-11-01T00:00:00Z"));
		withdrawn = userRepository.save(withdrawn);
		withdrawn.withdraw(java.time.Instant.parse("2026-11-02T00:00:00Z"));
		userRepository.save(withdrawn);

		assertThat(run(withdrawn.getId(), List.of(), List.of())).isEqualTo(CourseSeedJob.Report.NOTHING);
		assertThat(count("SELECT COUNT(*) FROM travel_rooms")).isZero();

		run(member.getId(), List.of(), List.of());

		assertThat(count("SELECT COUNT(*) FROM room_members WHERE user_id = ?", member.getId())).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM users WHERE provider_subject = ?", SeedOwner.SYSTEM_SUBJECT)).isZero();
	}

	@Test
	void 내리면_014_015_016_에서_사라지고_두_번_내려도_오류가_없고_다음_실행에서_다시_들어가지_않는다() throws IOException {
		bundle("b1", List.of(twoDays("강릉 1박2일"), oneDay()));
		run();
		Long twoDaysId = recordOf("c-two");

		assertThat(run(null, List.of("c-two"), List.of()).unpublished()).isEqualTo(1);

		assertThat(mainCourseService.recommend(null, null, null, null).items()).extracting(MainCourseListResponse.Item::id)
			.doesNotContain(twoDaysId);
		assertThat(searchService.search(CourseSearchCondition.of(null, null, null, null, null, null), null, null)
			.items()).extracting(item -> item.title()).containsExactly("당일 코스");
		assertThatThrownBy(() -> detailService.getDetail(twoDaysId)).isInstanceOfSatisfying(BusinessException.class,
			e -> assertThat(e.getExceptionType()).isEqualTo(ExceptionType.COURSE_UNAVAILABLE));
		assertThat(run(null, List.of("c-two"), List.of()).unpublished()).isZero();
		assertThat(run().inserted()).isZero();  // 내린 코스는 묶음에 남아 있어도 다시 들어가지 않는다
		assertThat(run(null, List.of(CourseSeedProperties.ALL), List.of()).unpublished()).isEqualTo(1);
	}

	@Test
	void 다시_넣으면_파일_내용으로_바뀌고_같은_코스가_두_개_보이지_않으며_같은_묶음으로는_반복하지_않는다() throws IOException {
		bundle("b1", List.of(twoDays("강릉 1박2일")));
		run();
		Long before = recordOf("c-two");
		bundle("b2", List.of(twoDays("강릉 1박2일 (고침)")));

		CourseSeedJob.Report report = run(null, List.of(), List.of("c-two"));

		assertThat(report.reseeded()).isEqualTo(1);
		Long after = recordOf("c-two");
		assertThat(after).isNotEqualTo(before);
		assertThat(detailService.getDetail(after).title()).isEqualTo("강릉 1박2일 (고침)");
		assertThat(searchService.search(CourseSearchCondition.of(null, null, null, null, null, null), null, null)
			.items()).extracting(item -> item.title()).containsExactly("강릉 1박2일 (고침)");
		assertThat(run(null, List.of(), List.of("c-two")).reseeded()).isZero();
		assertThat(recordOf("c-two")).isEqualTo(after);
	}

	@Test
	void 다시_넣을_코스가_묶음에_없으면_실패로_남기고_지금_코스는_그대로_둔다() throws IOException {
		bundle("b1", List.of(oneDay()));
		run();

		CourseSeedJob.Report report = run(null, List.of(), List.of("c-missing"));

		assertThat(report.failed()).containsEntry("c-missing", "NOT_IN_BUNDLE");
		assertThat(recordOf("c-one")).isNotNull();
	}

	@Test
	void 모양이_어긋난_코스는_그_코스만_실패한다() throws IOException {
		List<Map<String, Object>> broken = new ArrayList<>(List.of(
			place("p5", 1, 1, "2023-05-01", "9시", "photos/p5.jpg"),
			place("p6", 1, 2, "2023-05-01", "10:00", null)));
		bundle("b1", List.of(course("c-bad", "시각 틀림", broken), oneDay()));

		CourseSeedJob.Report report = run();

		assertThat(report.failed()).containsEntry("c-bad", "INVALID_TIME");
		assertThat(report.inserted()).isEqualTo(1);
	}

	/** AC-15: 두 번째 묶음을 넣으면 새 코스만 늘어난다(앞 묶음 코스가 다시 들어 있어도). */
	@Test
	void 두_번째_묶음을_넣으면_새_코스만_늘어난다() throws IOException {
		bundle("b1", List.of(oneDay()));
		run();
		Long first = recordOf("c-one");
		bundle("b2", List.of(oneDay(), twoDays("강릉 1박2일")));

		CourseSeedJob.Report report = run();

		assertThat(List.of(report.inserted(), report.skipped())).containsExactly(1, 2);
		assertThat(recordOf("c-one")).isEqualTo(first);
		assertThat(count("SELECT COUNT(*) FROM travel_rooms WHERE deleted_at IS NULL")).isEqualTo(2);
	}

	@Test
	void 사진_해시가_다르면_그_사진은_빼고_날마다_사진이_없으면_넣지_않는다() throws IOException {
		Map<String, Object> course = oneDay();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> places = (List<Map<String, Object>>)course.get("places");
		places.getFirst().put("photoSha256", "0".repeat(64));
		bundle("b1", List.of(course));

		assertThat(run().failed()).containsEntry("c-one", "PHOTO_MISSING_DAY");
	}

	@Test
	void 위치가_없으면_아무것도_하지_않는다() {
		assertThat(job.run(new CourseSeedProperties(true, null, " ", null, null, null)))
			.isEqualTo(CourseSeedJob.Report.NOTHING);
		assertThat(count("SELECT COUNT(*) FROM users")).isZero();
	}
}
