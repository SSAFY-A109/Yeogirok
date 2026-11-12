package com.ssafy.yeogirok.domain.room.service;

import static com.ssafy.yeogirok.domain.room.RoomTestConfig.LEGAL_DONG;
import static com.ssafy.yeogirok.domain.room.RoomTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ssafy.yeogirok.domain.place.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.place.tour.TourPlace;
import com.ssafy.yeogirok.domain.recommendation.RecommendationTestConfig;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationCommand;
import com.ssafy.yeogirok.domain.recommendation.dto.PlaceRecommendationResponse;
import com.ssafy.yeogirok.domain.recommendation.dto.PreferenceSaveCommand;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationEventType;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationLog;
import com.ssafy.yeogirok.domain.recommendation.entity.RecommendationMode;
import com.ssafy.yeogirok.domain.recommendation.service.PlaceRecommendationService;
import com.ssafy.yeogirok.domain.recommendation.service.RecommendationPreferenceService;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceAddCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceAddResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceResponse;
import com.ssafy.yeogirok.domain.room.entity.PlaceSourceType;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.CommittedRoomData;
import com.ssafy.yeogirok.support.FakeTourApiClient;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestUsers;

/**
 * 추천 담기(API-029 AI). 추천(API-034) → 담기까지 실제 흐름으로 본다. 서비스가 트랜잭션을 직접 나누므로(장소 만들기·후보 저장)
 * 테스트 트랜잭션을 쓰지 않고, 매 테스트 새 사용자·방·장소 ID 를 쓴다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(RecommendationTestConfig.class)
class RoomPlaceAddServiceTest {

	static long nextUserId = 9000L;
	static long nextContentId = 500_000L;

	@Autowired
	DataSource dataSource;
	@Autowired
	RoomPlaceAddService addService;
	@Autowired
	PlaceRecommendationService recommendationService;
	@Autowired
	RecommendationPreferenceService preferenceService;
	@Autowired
	RoomService roomService;
	@Autowired
	RoomInvitationService roomInvitationService;
	@Autowired
	FakeTourApiClient tourApi;
	@Autowired
	TestEntityManager em;
	@Autowired
	TransactionTemplate transactionTemplate;
	@Autowired
	MutableClock clock;

	Long owner;
	Long friend;
	Long roomId;
	String cafe;
	String bakery;

	/** 이 테스트가 만든 사용자. 끝나면 이 사용자들의 방·후보·장소·추천 기록과 함께 지운다(같은 DB 로 다시 돌릴 수 있게). */
	final List<Long> createdUsers = new ArrayList<>();

	private Long newUserId() {
		Long id = nextUserId++;
		createdUsers.add(id);
		return id;
	}

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		tourApi.reset();
		owner = newUserId();
		friend = newUserId();
		inTransaction(() -> TestUsers.insertActive(em.getEntityManager(), owner, friend));
		roomId = room(owner);
		roomInvitationService.accept(friend, roomInvitationService.getOrCreateInvitation(owner, roomId).inviteToken());
		cafe = String.valueOf(nextContentId++);
		bakery = String.valueOf(nextContentId++);
		tourApi.willReturn("FD/FD05", new TourPlace(cafe, "바다 카페", "강릉시 창해로 1", new BigDecimal("37.7951"),
			new BigDecimal("128.9183"), "http://img/cafe.jpg", "FD", "FD05", "FD050100"));
		tourApi.willReturn("FD/FD03", new TourPlace(bakery, "빵집", null, new BigDecimal("37.7700"),
			new BigDecimal("128.9000"), null, "FD", "FD03", "FD030100"));
	}

	@AfterEach
	void tearDown() {
		CommittedRoomData.delete(new JdbcTemplate(dataSource), createdUsers);
	}

	private Long room(Long user) {
		Long id = roomService.create(user, new RoomCreateCommand("강릉 여행", null, null, TransportMode.CAR)).id();
		preferenceService.save(user, id, new PreferenceSaveCommand(1, "51150", List.of("CAFE")));
		return id;
	}

	private PlaceRecommendationResponse recommend(Long user, Long room) {
		return recommendationService.recommend(user, room, PlaceRecommendationCommand.EMPTY);
	}

	private void inTransaction(Runnable work) {
		transactionTemplate.executeWithoutResult(status -> work.run());
	}

	private <T> T read(Supplier<T> work) {
		return transactionTemplate.execute(status -> work.get());
	}

	private int roomVersion() {
		return read(() -> em.find(TravelRoom.class, roomId).getVersion());
	}

	private long placeRows(String contentId) {
		return read(() -> em.getEntityManager().createQuery(
				"select count(p) from Place p where p.provider = :provider and p.providerPlaceId = :id", Long.class)
			.setParameter("provider", PlaceProvider.TOUR_API).setParameter("id", contentId).getSingleResult());
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 추천_결과를_담으면_장소를_만들고_AI_후보와_ADD_기록을_남기고_version_을_올린다() {
		PlaceRecommendationResponse recommended = recommend(owner, roomId);
		int before = roomVersion();

		RoomPlaceAddResponse response = addService.add(friend, roomId, new RoomPlaceAddCommand(List.of(
			new RoomPlaceAddCommand.Item(cafe, recommended.recommendationRequestId(), 1))));

		assertThat(response.addedCount()).isEqualTo(1);
		assertThat(response.version()).isEqualTo(before + 1).isEqualTo(roomVersion());
		RoomPlaceResponse added = response.added().getFirst();
		assertThat(added.sourceType()).isEqualTo(PlaceSourceType.AI);
		assertThat(added.addedBy()).isEqualTo(friend);
		assertThat(added.place().provider()).isEqualTo(PlaceProvider.TOUR_API);
		assertThat(added.place().providerPlaceId()).isEqualTo(cafe);
		assertThat(added.place().name()).isEqualTo("바다 카페");
		assertThat(added.place().categoryCode()).isEqualTo("CAFE");
		assertThat(added.place().regionCode()).isEqualTo(LEGAL_DONG.code());
		assertThat(added.place().regionName()).isEqualTo(LEGAL_DONG.name());
		assertThat(added.place().imageAttribution()).isEqualTo("한국관광공사");

		assertThat(read(() -> em.find(RoomPlace.class, added.id()).getPlace().getSyncedAt())).isEqualTo(NOW);

		List<RecommendationLog> adds = read(() -> em.getEntityManager().createQuery(
				"select l from RecommendationLog l where l.roomId = :room and l.eventType = :type", RecommendationLog.class)
			.setParameter("room", roomId).setParameter("type", RecommendationEventType.ADD).getResultList());
		assertThat(adds).singleElement().satisfies(log -> {
			assertThat(log.getUserId()).isEqualTo(friend);
			assertThat(log.getPlaceKey()).isEqualTo("TOUR_API:" + cafe);
			assertThat(log.getRank()).isEqualTo(1);
			assertThat(log.getMode()).isEqualTo(RecommendationMode.REGIONAL_POPULAR);
			assertThat(log.getInputPayload())
				.containsEntry("recommendationRequestId", recommended.recommendationRequestId().intValue())
				.containsEntry("roomPlaceId", added.id().intValue());
			assertThat(log.getPlaceSnapshot()).containsEntry("name", "바다 카페");
		});
	}

	@Test
	void 같은_장소는_places_한_행을_재사용하고_후보는_담을_때마다_새로_만든다() {
		PlaceRecommendationResponse recommended = recommend(owner, roomId);
		RoomPlaceAddCommand command = new RoomPlaceAddCommand(List.of(
			new RoomPlaceAddCommand.Item(cafe, recommended.recommendationRequestId(), 1),
			new RoomPlaceAddCommand.Item(cafe, recommended.recommendationRequestId(), 1),
			new RoomPlaceAddCommand.Item(bakery, recommended.recommendationRequestId(), 2)));

		RoomPlaceAddResponse first = addService.add(owner, roomId, command);
		// 다른 방에서 같은 장소를 담아도 같은 행을 쓴다.
		Long otherRoom = room(friend);
		PlaceRecommendationResponse other = recommend(friend, otherRoom);
		addService.add(friend, otherRoom, new RoomPlaceAddCommand(List.of(
			new RoomPlaceAddCommand.Item(cafe, other.recommendationRequestId(), 1))));

		assertThat(first.added()).extracting(RoomPlaceResponse::id).doesNotHaveDuplicates().hasSize(3);
		assertThat(first.added()).extracting(added -> added.place().providerPlaceId()).containsExactly(cafe, cafe, bakery);
		assertThat(placeRows(cafe)).isEqualTo(1);
		assertThat(placeRows(bakery)).isEqualTo(1);
	}

	@Test
	void 추천_결과와_다르면_C003_이고_아무것도_바꾸지_않는다() {
		PlaceRecommendationResponse recommended = recommend(owner, roomId);
		Long otherRoom = room(friend);
		PlaceRecommendationResponse otherRoomResult = recommend(friend, otherRoom);
		int before = roomVersion();

		// 순위의 장소가 다르다
		assertError(() -> addService.add(owner, roomId, new RoomPlaceAddCommand(List.of(
			new RoomPlaceAddCommand.Item(bakery, recommended.recommendationRequestId(), 1)))), ExceptionType.NOT_ALLOWED_VALUE);
		// 없는 순위
		assertError(() -> addService.add(owner, roomId, new RoomPlaceAddCommand(List.of(
			new RoomPlaceAddCommand.Item(cafe, recommended.recommendationRequestId(), 9)))), ExceptionType.NOT_ALLOWED_VALUE);
		// 다른 방의 추천 결과, 없는 추천 ID
		assertError(() -> addService.add(owner, roomId, new RoomPlaceAddCommand(List.of(
			new RoomPlaceAddCommand.Item(cafe, otherRoomResult.recommendationRequestId(), 1)))),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> addService.add(owner, roomId, new RoomPlaceAddCommand(List.of(
			new RoomPlaceAddCommand.Item(cafe, 987_654_321L, 1)))), ExceptionType.NOT_ALLOWED_VALUE);
		// 하나라도 틀리면 맞는 항목도 담지 않는다
		assertError(() -> addService.add(owner, roomId, new RoomPlaceAddCommand(List.of(
			new RoomPlaceAddCommand.Item(cafe, recommended.recommendationRequestId(), 1),
			new RoomPlaceAddCommand.Item(cafe, recommended.recommendationRequestId(), 2)))), ExceptionType.NOT_ALLOWED_VALUE);

		assertThat(roomVersion()).isEqualTo(before);
		assertThat(placeRows(cafe)).isZero();
	}

	@Test
	void 재전송은_담았던_후보와_지금_version_을_준다() {
		PlaceRecommendationResponse recommended = recommend(owner, roomId);
		RoomPlaceAddResponse first = addService.add(owner, roomId, new RoomPlaceAddCommand(List.of(
			new RoomPlaceAddCommand.Item(bakery, recommended.recommendationRequestId(), 2),
			new RoomPlaceAddCommand.Item(cafe, recommended.recommendationRequestId(), 1))));

		String resultId = RoomPlaceAddService.resultIdOf(first);
		RoomPlaceAddResponse replay = addService.getAdded(owner, roomId, RoomPlaceAddService.roomPlaceIdsOf(resultId));

		assertThat(replay.added()).extracting(RoomPlaceResponse::id)
			.containsExactlyElementsOf(first.added().stream().map(RoomPlaceResponse::id).toList());
		assertThat(replay.addedCount()).isEqualTo(2);
		assertThat(replay.version()).isEqualTo(first.version());
	}

	@Test
	void 참여자가_아니면_RO002() {
		PlaceRecommendationResponse recommended = recommend(owner, roomId);
		Long stranger = newUserId();
		inTransaction(() -> TestUsers.insertActive(em.getEntityManager(), stranger));

		assertError(() -> addService.add(stranger, roomId, new RoomPlaceAddCommand(List.of(
			new RoomPlaceAddCommand.Item(cafe, recommended.recommendationRequestId(), 1)))), ExceptionType.ROOM_NOT_MEMBER);
	}
}
