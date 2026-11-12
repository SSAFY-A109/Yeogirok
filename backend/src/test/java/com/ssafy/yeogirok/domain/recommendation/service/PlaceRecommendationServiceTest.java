package com.ssafy.yeogirok.domain.recommendation.service;

import static com.ssafy.yeogirok.domain.room.RoomTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.yeogirok.domain.category.TravelCategory;
import com.ssafy.yeogirok.domain.category.entity.TravelCategoryEntity;
import com.ssafy.yeogirok.domain.place.entity.Place;
import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.tour.TourAreaQuery;
import com.ssafy.yeogirok.domain.place.tour.TourPlace;
import com.ssafy.yeogirok.domain.recommendation.RecommendationTestConfig;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationCommand;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationResponse;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationResponse.Item;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRef;
import com.ssafy.yeogirok.domain.recommendation.dto.PreferenceSaveCommand;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationLog;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationMode;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.service.RoomPlaceService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.CommittedRoomData;
import com.ssafy.yeogirok.support.FakeTourApiClient;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestUsers;

/**
 * 추천 조건 → TourAPI(가짜) → 점수 → 추천 기록. 서비스가 트랜잭션을 직접 열고 닫으므로 테스트 트랜잭션을 쓰지 않는다
 * (테스트 메서드가 감싸면 서비스의 트랜잭션이 거기 합류해 실제 흐름과 달라진다). 대신 매 테스트 새 사용자·방을 쓴다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(RecommendationTestConfig.class)
class PlaceRecommendationServiceTest {

	static long nextUserId = 8000L;

	@Autowired
	DataSource dataSource;
	@Autowired
	PlaceRecommendationService recommendationService;
	@Autowired
	RecommendationPreferenceService preferenceService;
	@Autowired
	RoomService roomService;
	@Autowired
	RoomPlaceService roomPlaceService;
	@Autowired
	FakeTourApiClient tourApi;
	@Autowired
	TestEntityManager em;
	@Autowired
	org.springframework.transaction.support.TransactionTemplate transactionTemplate;
	@Autowired
	MutableClock clock;

	Long owner;
	Long roomId;

	/** @param small 소분류 코드(8자리). 대·중분류는 앞자리로 정한다 */
	static TourPlace tour(String id, String lat, String lng, String small) {
		return new TourPlace(id, "장소" + id, "주소" + id, new BigDecimal(lat), new BigDecimal(lng), "http://img/" + id,
			small.substring(0, 2), small.substring(0, 4), small);
	}

	static TourPlace untitledImage(String id, String small) {
		return new TourPlace(id, "장소" + id, null, new BigDecimal("37.7"), new BigDecimal("128.9"), null,
			small == null ? null : small.substring(0, 2), small == null ? null : small.substring(0, 4), small);
	}

	/** 이 테스트가 만든 사용자. 끝나면 이 사용자들의 방·후보·장소·추천 기록과 함께 지운다(같은 DB 로 다시 돌릴 수 있게). */
	final List<Long> createdUsers = new ArrayList<>();

	private Long newUserId() {
		Long id = nextUserId++;
		createdUsers.add(id);
		return id;
	}

	/** 테스트 전 카테고리 행(PostgreSQL 이면 Flyway V2 기준 데이터). 테스트는 빈 상태로 돌고 끝나면 되돌린다. */
	List<Map<String, Object>> savedCategories;

	@BeforeEach
	void setUp() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		savedCategories = jdbc.queryForList("SELECT code, name FROM travel_categories");
		jdbc.update("DELETE FROM travel_categories");
		clock.set(NOW);
		tourApi.reset();
		owner = newUserId();
		inTransaction(() -> TestUsers.insertActive(em.getEntityManager(), owner));
		roomId = roomService.create(owner, new RoomCreateCommand("강릉 여행", null, null, TransportMode.CAR)).id();
	}

	private void inTransaction(Runnable work) {
		transactionTemplate.executeWithoutResult(status -> work.run());
	}

	private void savePreferences(String... categories) {
		preferenceService.save(owner, roomId, new PreferenceSaveCommand(1, "51150", List.of(categories)));
	}

	private PlaceRecommendationResponse recommend() {
		return recommendationService.recommend(owner, roomId, PlaceRecommendationCommand.EMPTY);
	}

	private static List<String> ids(PlaceRecommendationResponse response) {
		return response.items().stream().map(item -> item.place().providerPlaceId()).toList();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	/** 테스트 트랜잭션 없이 커밋하므로, 카테고리 행은 테스트 전 상태로 되돌리고 만든 사용자·방·장소는 실패해도 지운다. */
	@AfterEach
	void tearDown() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("DELETE FROM travel_categories");
		savedCategories.forEach(row -> jdbc.update("INSERT INTO travel_categories (code, name) VALUES (?, ?)",
			row.get("code"), row.get("name")));
		CommittedRoomData.delete(jdbc, createdUsers);
	}

	/** 다른 방이 TourAPI 장소를 담은 기록(인기 집계용). 담은 시각을 정하려고 행을 직접 만든다. */
	private void addTourPlaceToNewRoom(String contentId, Instant addedAt, boolean deleteRoom) {
		Long user = newUserId();
		inTransaction(() -> TestUsers.insertActive(em.getEntityManager(), user));
		Long otherRoom = roomService.create(user, new RoomCreateCommand("다른 방", null, null, TransportMode.CAR)).id();
		addTourPlace(otherRoom, user, contentId, addedAt, deleteRoom);
	}

	/** 장소 좌표는 37.7, 128.9 다. */
	private void addTourPlace(Long targetRoom, Long user, String contentId, Instant addedAt, boolean deleteRoom) {
		inTransaction(() -> {
			var entityManager = em.getEntityManager();
			Place place = entityManager.createQuery(
					"select p from Place p where p.provider = :provider and p.providerPlaceId = :id", Place.class)
				.setParameter("provider", PlaceProvider.TOUR_API).setParameter("id", contentId)
				.getResultStream().findFirst().orElseGet(() -> {
					Place created = Place.manual("장소" + contentId, new BigDecimal("37.7"), new BigDecimal("128.9"),
						null, null, null, addedAt);
					ReflectionTestUtils.setField(created, "provider", PlaceProvider.TOUR_API);
					ReflectionTestUtils.setField(created, "providerPlaceId", contentId);
					entityManager.persist(created);
					return created;
				});
			entityManager.persist(RoomPlace.manual(targetRoom, place, user, addedAt));
			if (deleteRoom) {
				entityManager.find(TravelRoom.class, targetRoom).softDelete(addedAt);
			}
		});
	}

	@Test
	void 조건을_저장하지_않았으면_C001_이고_TourAPI_를_부르지_않는다() {
		assertError(this::recommend, ExceptionType.REQUIRED_VALUE_MISSING);
		assertThat(tourApi.calls()).isEmpty();
	}

	@Test
	void 담은_후보가_없으면_지역_인기_방식이고_추천_기록을_남긴다() {
		savePreferences("CAFE");
		tourApi.willReturn("FD/FD05", tour("a", "37.70", "128.90", "FD050100"), tour("b", "37.71", "128.90", "FD050200"));
		// 간이음식 중 제과만 카페다(소분류 예외). 나머지 간이음식은 맛집이라 뺀다.
		tourApi.willReturn("FD/FD03", tour("bakery", "37.72", "128.90", "FD030100"),
			tour("snack", "37.72", "128.90", "FD030200"));
		long placesBefore = inTransactionCount("select count(p) from Place p");

		PlaceRecommendationResponse response = recommend();

		assertThat(response.mode()).isEqualTo(RecommendationMode.REGIONAL_POPULAR);
		assertThat(ids(response)).containsExactly("a", "b", "bakery");
		assertThat(response.generatedAt()).isEqualTo(NOW);
		Item first = response.items().getFirst();
		assertThat(first.rank()).isEqualTo(1);
		assertThat(first.place()).isEqualTo(new PlaceRecommendationResponse.RecommendedPlace("TOUR_API", "a", "장소a",
			new BigDecimal("37.70"), new BigDecimal("128.90"), "주소a", "CAFE", "http://img/a", "한국관광공사"));
		assertThat(first.reason()).isEqualTo("선택한 카테고리");
		// 동시에 부르므로 호출 순서는 정해져 있지 않다(결과는 조회 조건 순서로 합친다).
		assertThat(tourApi.calls()).containsExactlyInAnyOrder(new TourAreaQuery("51", "150", "FD", "FD05", 100),
			new TourAreaQuery("51", "150", "FD", "FD03", 100));

		RecommendationLog log = inTransactionResult(() -> em.find(RecommendationLog.class,
			response.recommendationRequestId()));
		assertThat(log.getRoomId()).isEqualTo(roomId);
		assertThat(log.getUserId()).isEqualTo(owner);
		assertThat(log.getMode()).isEqualTo(RecommendationMode.REGIONAL_POPULAR);
		assertThat(log.getInputPayload()).containsEntry("regionCode", "51150").containsEntry("limit", 10)
			.containsEntry("roomPlaceCount", 0).containsEntry("tourApiCalls", 2).containsEntry("tourApiFailures", 0)
			.containsEntry("fetchedPlaceCount", 3).doesNotContainKey("categoryCount");
		// 추천만으로 places 행을 만들지 않는다(담기 때 만든다).
		assertThat(inTransactionCount("select count(p) from Place p")).isEqualTo(placesBefore);
	}

	@Test
	void 담은_후보가_있으면_개인화_방식이고_가까운_곳이_먼저다() {
		savePreferences("NATURE");
		roomPlaceService.addCustomPlace(owner, roomId,
			new CustomPlaceCreateCommand("강릉역", new BigDecimal("37.7640"), new BigDecimal("128.8990"), null));
		tourApi.willReturn("NA", tour("far", "37.90", "128.90", "NA010100"), tour("near", "37.775", "128.899", "NA020100"));

		PlaceRecommendationResponse response = recommend();

		assertThat(response.mode()).isEqualTo(RecommendationMode.PERSONALIZED);
		assertThat(ids(response)).containsExactly("near", "far");
		assertThat(response.items().getFirst().reasonEvidence()).containsExactly("카테고리 일치", "담은 장소들과 1.2km");
	}

	@Test
	void 인기는_최근_30일_삭제되지_않은_다른_방만_센다() {
		inTransaction(() -> em.persist(new TravelCategoryEntity("CAFE", "카페")));
		savePreferences("CAFE");
		tourApi.willReturn("FD/FD05", tour("quiet", "37.70", "128.90", "FD050100"),
			tour("hot", "37.71", "128.90", "FD050100"));
		addTourPlaceToNewRoom("hot", NOW.minus(Duration.ofDays(1)), false);
		addTourPlaceToNewRoom("hot", NOW.minus(Duration.ofDays(2)), false);
		addTourPlaceToNewRoom("hot", NOW.minus(Duration.ofDays(3)), true);
		addTourPlaceToNewRoom("quiet", NOW.minus(Duration.ofDays(40)), false);
		// 이 방이 직접 담은 것은 인기로 세지 않는다(담은 장소 좌표 37.7, 128.9 는 동선 중심점이 된다).
		addTourPlace(roomId, owner, "quiet", NOW.minus(Duration.ofDays(1)), false);

		PlaceRecommendationResponse response = recommend();

		assertThat(ids(response)).containsExactly("hot", "quiet");
		assertThat(response.items().getFirst().reason())
			.isEqualTo("카페 · 담은 장소들과 1.1km · 최근 30일 여행방 2곳이 담았어요");
		assertThat(response.items().get(1).reason()).isEqualTo("카페 · 담은 장소들과 0m");
	}

	@Test
	void 고른_카테고리가_아니거나_카테고리_없는_장소와_제외_장소는_뺀다() {
		savePreferences("FOOD");
		// FD 의 중분류 5개 중 맛집은 4개(FD05 카페 제외)라 중분류마다 부른다.
		tourApi.willReturn("FD/FD01", untitledImage("kimbap", "FD010100"), untitledImage("coffee", "FD050100"),
			untitledImage("noodle", "FD010200"), untitledImage("unknown", null));
		tourApi.willReturn("FD/FD03", untitledImage("bakery", "FD030100"), untitledImage("snack", "FD030200"));

		PlaceRecommendationResponse response = recommendationService.recommend(owner, roomId,
			new PlaceRecommendationCommand(null, List.of(new PlaceRef("TOUR_API", "noodle"))));

		assertThat(ids(response)).containsExactly("kimbap", "snack");
		assertThat(response.items().getFirst().place().imageAttribution()).isNull();
		assertThat(tourApi.calls()).extracting(TourAreaQuery::lclsSystm2)
			.containsExactlyInAnyOrder("FD01", "FD02", "FD03", "FD04");
	}

	@Test
	void 같은_대분류의_두_카테고리는_한_번_불러_나누고_번갈아_낸다() {
		savePreferences("FOOD", "CAFE");
		tourApi.willReturn("FD", untitledImage("k1", "FD010100"), untitledImage("k2", "FD020100"),
			untitledImage("c1", "FD050100"), untitledImage("c2", "FD030100"));

		PlaceRecommendationResponse response = recommend();

		assertThat(ids(response)).containsExactly("k1", "c1", "k2", "c2");
		assertThat(response.items()).extracting(item -> item.place().categoryCode())
			.containsExactly("FOOD", "CAFE", "FOOD", "CAFE");
		assertThat(tourApi.calls()).hasSize(1);
	}

	@Test
	void 일부_조회만_실패하면_나머지로_추천하고_모두_실패하면_C014() {
		savePreferences("ACTIVITY");
		// 체험: LS 는 중분류 4개를 다 써서 대분류 전체, EX(7개 중 5개)·VE(8개 중 2개)는 중분류마다.
		tourApi.willFail("EX/EX02", "EX/EX03");
		tourApi.willReturn("LS", tour("ski", "37.7", "128.9", "LS010100"));
		assertThat(ids(recommend())).containsExactly("ski");
		assertThat(tourApi.calls()).extracting(TourAreaQuery::lclsSystm1, TourAreaQuery::lclsSystm2)
			.containsExactlyInAnyOrder(tuple("EX", "EX02"), tuple("EX", "EX03"), tuple("EX", "EX05"),
				tuple("EX", "EX06"), tuple("EX", "EX07"), tuple("LS", null), tuple("VE", "VE02"), tuple("VE", "VE10"));

		tourApi.willFail("EX/EX05", "EX/EX06", "EX/EX07", "LS", "VE/VE02", "VE/VE10");
		assertError(this::recommend, ExceptionType.SERVER_ERROR);
	}

	@Test
	void 마감_시간을_넘긴_조회는_기다리지_않고_나머지로_추천한다() {
		savePreferences("NATURE");
		tourApi.willDelay("NA", Duration.ofSeconds(5));
		tourApi.willReturn("NA", tour("slow", "37.7", "128.9", "NA010100"));
		tourApi.willReturn("VE/VE03", tour("park", "37.7", "128.9", "VE030100"));

		long started = System.nanoTime();
		PlaceRecommendationResponse response = recommend();

		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(4));
		assertThat(ids(response)).containsExactly("park");
	}

	@Test
	void 모든_카테고리를_골라도_대분류_수_8번만_부른다() {
		List<TourAreaQuery> queries = PlaceRecommendationService.planQueries(List.of(TravelCategory.values()),
			"51150", 100, 12);

		assertThat(queries).hasSize(8).allSatisfy(query -> assertThat(query.lclsSystm2()).isNull());
		assertThat(queries).extracting(TourAreaQuery::lclsSystm1)
			.containsExactlyInAnyOrder("NA", "VE", "EX", "HS", "FD", "LS", "SH", "EV");
	}

	@Test
	void 대분류의_일부_중분류만_쓰면_중분류마다_부른다() {
		// 랜드마크는 VE(카테고리가 붙는 중분류 8개) 중 3개만 쓴다. 대분류 전체로 받으면 다른 중분류가 자리를 차지한다.
		assertThat(PlaceRecommendationService.planQueries(List.of(TravelCategory.SIGHT), "51150", 100, 12))
			.extracting(TourAreaQuery::lclsSystm1, TourAreaQuery::lclsSystm2)
			.containsExactly(tuple("VE", "VE01"), tuple("VE", "VE04"), tuple("VE", "VE05"));
		// 자연 힐링은 NA 전체 + VE03 + EX05.
		assertThat(PlaceRecommendationService.planQueries(List.of(TravelCategory.NATURE), "51150", 100, 12))
			.extracting(TourAreaQuery::lclsSystm1, TourAreaQuery::lclsSystm2)
			.containsExactlyInAnyOrder(tuple("NA", null), tuple("VE", "VE03"), tuple("EX", "EX05"));
	}

	@Test
	void 호출_상한을_넘으면_중분류가_가장_많은_대분류부터_전체_조회로_바꾼다() {
		List<TravelCategory> categories = List.of(TravelCategory.NATURE, TravelCategory.CULTURE,
			TravelCategory.ACTIVITY);
		// NA·HS·LS·EX 는 전체, VE 는 VE02·VE03·VE06·VE07·VE10 다섯 번 → 9번.
		assertThat(PlaceRecommendationService.planQueries(categories, "51150", 100, 12)).hasSize(9);
		// 상한 8 이면 VE 도 전체로 바꿔 5번.
		assertThat(PlaceRecommendationService.planQueries(categories, "51150", 100, 8))
			.hasSize(5).allSatisfy(query -> assertThat(query.lclsSystm2()).isNull());
	}

	@Test
	void TourAPI_결과가_없으면_빈_목록으로_성공한다() {
		savePreferences("NATURE");

		assertThat(recommend().items()).isEmpty();
	}

	@Test
	void limit_만큼만_주고_범위를_벗어나면_C003() {
		savePreferences("NATURE");
		tourApi.willReturn("NA", tour("1", "37.7", "128.9", "NA010100"), tour("2", "37.7", "128.9", "NA010100"),
			tour("3", "37.7", "128.9", "NA010100"));

		assertThat(ids(recommendationService.recommend(owner, roomId, new PlaceRecommendationCommand(2, List.of()))))
			.containsExactly("1", "2");
		assertError(() -> recommendationService.recommend(owner, roomId, new PlaceRecommendationCommand(0, List.of())),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> recommendationService.recommend(owner, roomId, new PlaceRecommendationCommand(51, List.of())),
			ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 재전송은_저장한_결과를_그대로_주고_TourAPI_를_다시_부르지_않는다() {
		savePreferences("CAFE");
		tourApi.willReturn("FD/FD05", tour("a", "37.70", "128.90", "FD050100"));
		PlaceRecommendationResponse first = recommend();
		int calls = tourApi.calls().size();

		PlaceRecommendationResponse replay = recommendationService.getRecommended(owner, roomId,
			first.recommendationRequestId());

		// JSON 으로 저장했다 읽어 좌표 자릿수(37.70 → 37.7)만 다를 수 있다. 값은 같아야 한다.
		assertThat(replay).usingRecursiveComparison()
			.withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(first);
		assertThat(tourApi.calls()).hasSize(calls);
	}

	@Test
	void 참여자가_아니면_RO002() {
		savePreferences("CAFE");
		Long stranger = newUserId();
		inTransaction(() -> TestUsers.insertActive(em.getEntityManager(), stranger));

		assertError(() -> recommendationService.recommend(stranger, roomId, PlaceRecommendationCommand.EMPTY),
			ExceptionType.ROOM_NOT_MEMBER);
		// limit 이 틀려도 참여 확인이 먼저다.
		assertError(() -> recommendationService.recommend(stranger, roomId,
			new PlaceRecommendationCommand(0, List.of())), ExceptionType.ROOM_NOT_MEMBER);
		assertThat(tourApi.calls()).isEmpty();
	}

	private long inTransactionCount(String jpql) {
		return inTransactionResult(() -> em.getEntityManager().createQuery(jpql, Long.class).getSingleResult());
	}

	private <T> T inTransactionResult(java.util.function.Supplier<T> work) {
		return transactionTemplate.execute(status -> work.get());
	}
}
