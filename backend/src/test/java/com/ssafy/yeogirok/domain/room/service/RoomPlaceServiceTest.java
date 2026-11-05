package com.ssafy.yeogirok.domain.room.service;

import static com.ssafy.yeogirok.domain.room.RoomTestConfig.LEGAL_DONG;
import static com.ssafy.yeogirok.domain.room.RoomTestConfig.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateResponse;
import com.ssafy.yeogirok.domain.room.dto.PlaceInfo;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceListResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceRemoveResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceRestoreResponse;
import com.ssafy.yeogirok.domain.room.dto.RoomPlaceState;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateCommand;
import com.ssafy.yeogirok.domain.room.entity.PlaceProvider;
import com.ssafy.yeogirok.domain.room.entity.PlaceSourceType;
import com.ssafy.yeogirok.domain.room.entity.RoomPlace;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.geo.LegalDong;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestUsers;

@DataJpaTest
@Import(RoomTestConfig.class)
class RoomPlaceServiceTest {

	static final Long OWNER = 6001L;
	static final Long FRIEND = 6002L;
	static final Long STRANGER = 6003L;
	static final BigDecimal LAT = new BigDecimal("37.7519000");
	static final BigDecimal LNG = new BigDecimal("128.8761000");

	@Autowired
	RoomPlaceService roomPlaceService;
	@Autowired
	RoomService roomService;
	@Autowired
	RoomInvitationService roomInvitationService;
	@Autowired
	FixedLegalDongResolver legalDongResolver;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;

	Long roomId;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		legalDongResolver.willReturn(LEGAL_DONG);
		TestUsers.insertActive(em.getEntityManager(), OWNER, FRIEND, STRANGER);
		roomId = roomService.create(OWNER, new RoomCreateCommand("강릉 여행", null, null, TransportMode.CAR)).id();
		roomInvitationService.accept(FRIEND,
			roomInvitationService.getOrCreateInvitation(OWNER, roomId).inviteToken());
	}

	private CustomPlaceCreateResponse add(Long userId, String name) {
		return roomPlaceService.addCustomPlace(userId, roomId, new CustomPlaceCreateCommand(name, LAT, LNG, null));
	}

	private int version() {
		return roomService.getRoom(OWNER, roomId).version();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	/* ── API-031 직접 추가 ──────────────────────────────────── */

	@Test
	void 직접_추가하면_MANUAL_후보가_생기고_법정동_이름을_저장하고_version이_오른다() {
		int before = version();

		CustomPlaceCreateResponse response = roomPlaceService.addCustomPlace(FRIEND, roomId,
			new CustomPlaceCreateCommand("경포 해변 주차장", LAT, LNG, "강원 강릉시 창해로"));

		assertThat(response.version()).isEqualTo(before + 1).isEqualTo(version());
		assertThat(response.roomPlace().sourceType()).isEqualTo(PlaceSourceType.MANUAL);
		assertThat(response.roomPlace().addedBy()).isEqualTo(FRIEND);
		assertThat(response.roomPlace().sourceRecordId()).isNull();
		assertThat(response.roomPlace().place()).isEqualTo(new PlaceInfo(PlaceProvider.MANUAL, null,
			"경포 해변 주차장", LAT, LNG, "강원 강릉시 창해로", null, null, null, "강릉동", null, null));
		assertThat(legalDongResolver.calls()).isEqualTo(1);

		em.flush();
		em.clear();
		RoomPlace saved = em.find(RoomPlace.class, response.roomPlace().id());
		assertThat(saved.getRoomId()).isEqualTo(roomId);
		assertThat(saved.getRegionName()).isEqualTo("강릉동");
		assertThat(saved.getRegionCode()).isNull();
		assertThat(saved.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void 법정동_이름을_못_얻어도_후보는_만들고_regionName은_null() {
		legalDongResolver.willReturn(LegalDong.EMPTY);

		assertThat(add(OWNER, "이름 없는 곳").roomPlace().place().regionName()).isNull();
	}

	@Test
	void 같은_장소를_여러_번_담으면_후보도_여러_개다() {
		Long first = add(OWNER, "경포대").roomPlace().id();
		Long second = add(OWNER, "경포대").roomPlace().id();

		assertThat(first).isNotEqualTo(second);
		assertThat(roomPlaceService.getPlaces(OWNER, roomId, RoomPlaceState.ACTIVE).items()).hasSize(2);
	}

	@Test
	void 참여자가_아니면_RO002_카카오도_부르지_않는다() {
		assertError(() -> add(STRANGER, "경포대"), ExceptionType.ROOM_NOT_MEMBER);
		roomService.leave(FRIEND, roomId);
		assertError(() -> add(FRIEND, "경포대"), ExceptionType.ROOM_NOT_MEMBER);
		assertThat(legalDongResolver.calls()).isZero();
	}

	@Test
	void 없거나_삭제된_방이면_RO001() {
		assertError(() -> roomPlaceService.addCustomPlace(OWNER, 999_999L,
			new CustomPlaceCreateCommand("경포대", LAT, LNG, null)), ExceptionType.ROOM_NOT_FOUND);
		roomService.leave(FRIEND, roomId);
		roomService.leave(OWNER, roomId);
		em.flush();
		assertError(() -> add(OWNER, "경포대"), ExceptionType.ROOM_NOT_FOUND);
	}

	@Test
	void 멱등_재조회는_담았던_후보와_지금_version을_준다() {
		CustomPlaceCreateResponse created = add(OWNER, "경포대");
		add(OWNER, "오죽헌");

		CustomPlaceCreateResponse again = roomPlaceService.getAddedCustomPlace(OWNER, roomId, created.roomPlace().id());

		assertThat(again.roomPlace()).isEqualTo(created.roomPlace());
		assertThat(again.version()).isEqualTo(version());
	}

	/* ── API-028 목록 ───────────────────────────────────────── */

	@Test
	void 목록은_state별로_담은_순서대로_주고_version은_여행방_version이다() {
		Long kept = add(OWNER, "경포대").roomPlace().id();
		Long removed = add(FRIEND, "오죽헌").roomPlace().id();
		clock.advance(Duration.ofMinutes(5));
		roomPlaceService.remove(OWNER, roomId, removed, version());

		RoomPlaceListResponse active = roomPlaceService.getPlaces(FRIEND, roomId, RoomPlaceState.ACTIVE);
		RoomPlaceListResponse removedOnly = roomPlaceService.getPlaces(FRIEND, roomId, RoomPlaceState.REMOVED);
		RoomPlaceListResponse all = roomPlaceService.getPlaces(FRIEND, roomId, RoomPlaceState.ALL);

		assertThat(active.items()).extracting(RoomPlaceListResponse.Item::id).containsExactly(kept);
		assertThat(active.items().getFirst().removedAt()).isNull();
		assertThat(removedOnly.items()).extracting(RoomPlaceListResponse.Item::id).containsExactly(removed);
		assertThat(removedOnly.items().getFirst().removedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
		assertThat(removedOnly.items().getFirst().addedBy()).isEqualTo(FRIEND);
		assertThat(all.items()).extracting(RoomPlaceListResponse.Item::id).containsExactly(kept, removed);
		assertThat(all.version()).isEqualTo(version());
		assertThat(all.items()).allSatisfy(item -> assertThat(item.sourceRecord()).isNull());
	}

	@Test
	void 목록은_참여자만_본다() {
		assertError(() -> roomPlaceService.getPlaces(STRANGER, roomId, RoomPlaceState.ACTIVE),
			ExceptionType.ROOM_NOT_MEMBER);
		assertError(() -> roomPlaceService.getPlaces(OWNER, 999_999L, RoomPlaceState.ACTIVE),
			ExceptionType.ROOM_NOT_FOUND);
	}

	/* ── API-032 빼기 ───────────────────────────────────────── */

	@Test
	void 빼면_removed_at만_채우고_version이_오르며_재계산_플래그는_그대로다() {
		Long placeId = add(OWNER, "경포대").roomPlace().id();
		int before = version();

		RoomPlaceRemoveResponse response = roomPlaceService.remove(FRIEND, roomId, placeId, before);

		assertThat(response).isEqualTo(new RoomPlaceRemoveResponse(true, before + 1, false, null));
		em.flush();
		em.clear();
		RoomPlace place = em.find(RoomPlace.class, placeId);
		assertThat(place.getRemovedAt()).isEqualTo(NOW);
		assertThat(place.getAddedBy()).isEqualTo(OWNER);
	}

	@Test
	void 이미_뺀_후보를_다시_빼면_version과_상관없이_바꾸지_않고_성공한다() {
		Long placeId = add(OWNER, "경포대").roomPlace().id();
		int staleVersion = version();
		roomPlaceService.remove(OWNER, roomId, placeId, staleVersion);
		int current = version();

		RoomPlaceRemoveResponse again = roomPlaceService.remove(FRIEND, roomId, placeId, staleVersion);

		assertThat(again).isEqualTo(new RoomPlaceRemoveResponse(true, current, false, null));
		assertThat(version()).isEqualTo(current);
	}

	@Test
	void 빼기에서_version이_다르면_C004() {
		Long placeId = add(OWNER, "경포대").roomPlace().id();

		assertError(() -> roomPlaceService.remove(OWNER, roomId, placeId, version() - 1),
			ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 없는_후보나_다른_방의_후보면_RO009() {
		Long otherRoom = roomService.create(OWNER, new RoomCreateCommand("다른 방", null, null, TransportMode.CAR)).id();
		Long otherPlace = roomPlaceService.addCustomPlace(OWNER, otherRoom,
			new CustomPlaceCreateCommand("다른 곳", LAT, LNG, null)).roomPlace().id();

		assertError(() -> roomPlaceService.remove(OWNER, roomId, otherPlace, version()),
			ExceptionType.ROOM_PLACE_NOT_FOUND);
		assertError(() -> roomPlaceService.remove(OWNER, roomId, 999_999L, version()),
			ExceptionType.ROOM_PLACE_NOT_FOUND);
		assertError(() -> roomPlaceService.restore(OWNER, roomId, otherPlace, version()),
			ExceptionType.ROOM_PLACE_NOT_FOUND);
	}

	@Test
	void 빼기와_복원은_참여자만_한다() {
		Long placeId = add(OWNER, "경포대").roomPlace().id();

		assertError(() -> roomPlaceService.remove(STRANGER, roomId, placeId, version()),
			ExceptionType.ROOM_NOT_MEMBER);
		assertError(() -> roomPlaceService.restore(STRANGER, roomId, placeId, version()),
			ExceptionType.ROOM_NOT_MEMBER);
	}

	/* ── API-094 복원 ───────────────────────────────────────── */

	@Test
	void 복원은_원래_행을_되살리고_version이_오른다() {
		Long placeId = add(FRIEND, "경포대").roomPlace().id();
		roomPlaceService.remove(OWNER, roomId, placeId, version());
		clock.advance(Duration.ofDays(400));
		int before = version();

		RoomPlaceRestoreResponse response = roomPlaceService.restore(OWNER, roomId, placeId, before);

		assertThat(response).isEqualTo(new RoomPlaceRestoreResponse(true, placeId, before + 1, false));
		RoomPlaceListResponse active = roomPlaceService.getPlaces(OWNER, roomId, RoomPlaceState.ACTIVE);
		assertThat(active.items()).singleElement().satisfies(item -> {
			assertThat(item.id()).isEqualTo(placeId);
			assertThat(item.addedBy()).isEqualTo(FRIEND);
			assertThat(item.sourceType()).isEqualTo(PlaceSourceType.MANUAL);
			assertThat(item.removedAt()).isNull();
		});
	}

	@Test
	void 이미_있는_후보를_복원하면_version과_상관없이_바꾸지_않고_성공한다() {
		Long placeId = add(OWNER, "경포대").roomPlace().id();
		int current = version();

		assertThat(roomPlaceService.restore(FRIEND, roomId, placeId, current - 1))
			.isEqualTo(new RoomPlaceRestoreResponse(true, placeId, current, false));
		assertThat(version()).isEqualTo(current);
	}

	@Test
	void 복원에서_version이_다르면_C004() {
		Long placeId = add(OWNER, "경포대").roomPlace().id();
		roomPlaceService.remove(OWNER, roomId, placeId, version());

		assertError(() -> roomPlaceService.restore(OWNER, roomId, placeId, version() - 1),
			ExceptionType.VERSION_MISMATCH);
	}

	/* ── 다른 API 와의 연결 ─────────────────────────────────── */

	@Test
	void 목록의_placeCount와_상세의_places는_빼지_않은_후보만_센다() {
		Long kept = add(OWNER, "경포대").roomPlace().id();
		Long removed = add(OWNER, "오죽헌").roomPlace().id();
		roomPlaceService.remove(OWNER, roomId, removed, version());

		assertThat(roomService.getMyRooms(OWNER, null, null).items()).singleElement()
			.satisfies(item -> assertThat(item.placeCount()).isEqualTo(1));
		assertThat(roomService.getRoom(OWNER, roomId).places()).singleElement()
			.satisfies(place -> assertThat(place.id()).isEqualTo(kept));
	}

	@Test
	void 후보가_바뀐_뒤_예전_version으로_방을_수정하면_C004() {
		int seen = version();
		add(OWNER, "경포대");

		assertError(() -> roomService.update(OWNER, roomId,
			new RoomUpdateCommand(seen, "새 제목", false, null, null, null, false)), ExceptionType.VERSION_MISMATCH);
	}
}
