package com.ssafy.yeogirok.domain.schedule.service;

import static com.ssafy.yeogirok.domain.room.RoomTestConfig.LEGAL_DONG;
import static com.ssafy.yeogirok.domain.room.RoomTestConfig.NOW;
import static com.ssafy.yeogirok.domain.room.RoomTestConfig.TRAVEL_MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.room.RoomTestConfig;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomUpdateResponse;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions;
import com.ssafy.yeogirok.domain.room.service.RoomInvitationService;
import com.ssafy.yeogirok.domain.room.service.RoomPlaceService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleResponse;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateCommand;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateCommand.ItemEdit;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateResponse;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.FakeTravelTimeProvider;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestUsers;

@DataJpaTest
@Import(RoomTestConfig.class)
class ScheduleServiceTest {

	static final Long OWNER = 7001L;
	static final Long FRIEND = 7002L;
	static final Long STRANGER = 7003L;
	static final LocalDate DAY1 = LocalDate.of(2026, 11, 5);
	static final LocalDate DAY2 = DAY1.plusDays(1);
	/** DAY1 10:00 Asia/Seoul. */
	static final Instant DAY1_10AM = Instant.parse("2026-11-05T01:00:00Z");

	@Autowired
	ScheduleService scheduleService;
	@Autowired
	RoomService roomService;
	@Autowired
	RoomPlaceService roomPlaceService;
	@Autowired
	RoomInvitationService roomInvitationService;
	@Autowired
	FakeTravelTimeProvider travelTimeProvider;
	@Autowired
	FixedLegalDongResolver legalDongResolver;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;

	Long roomId;
	Long placeA;
	Long placeB;
	Long placeC;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		legalDongResolver.willReturn(LEGAL_DONG);
		travelTimeProvider.willReturn(TRAVEL_MINUTES);
		TestUsers.insertActive(em.getEntityManager(), OWNER, FRIEND, STRANGER);
		roomId = roomService.create(OWNER, new RoomCreateCommand("강릉 여행", DAY1, DAY2, TransportMode.CAR)).id();
		roomInvitationService.accept(FRIEND,
			roomInvitationService.getOrCreateInvitation(OWNER, roomId).inviteToken());
		placeA = addPlace("경포대");
		placeB = addPlace("안목해변");
		placeC = addPlace("오죽헌");
	}

	private Long addPlace(String name) {
		return roomPlaceService.addCustomPlace(OWNER, roomId, new CustomPlaceCreateCommand(name,
			new BigDecimal("37.7519000"), new BigDecimal("128.8761000"), null)).roomPlace().id();
	}

	private int version() {
		return scheduleService.getSchedule(OWNER, roomId).version();
	}

	private ScheduleUpdateResponse update(ItemEdit... items) {
		return scheduleService.update(OWNER, roomId, new ScheduleUpdateCommand(version(), List.of(items), List.of()));
	}

	private static ItemEdit newItem(Long placeId, LocalDate date, int order, int stay) {
		return new ItemEdit(null, placeId, date, order, null, stay, null);
	}

	private static List<ScheduleView.Item> items(ScheduleView schedule, LocalDate date) {
		return schedule.days().stream().filter(day -> day.date().equals(date)).findFirst().orElseThrow().items();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	/* ── API-036 조회 ───────────────────────────────────────── */

	@Test
	void 일정이_없으면_schedule은_null이다() {
		ScheduleResponse response = scheduleService.getSchedule(FRIEND, roomId);

		assertThat(response.schedule()).isNull();
		assertThat(response.version()).isEqualTo(4);
	}

	@Test
	void 참여자가_아니면_RO002() {
		assertError(() -> scheduleService.getSchedule(STRANGER, roomId), ExceptionType.ROOM_NOT_MEMBER);
		assertError(() -> scheduleService.update(STRANGER, roomId,
			new ScheduleUpdateCommand(4, List.of(), List.of())), ExceptionType.ROOM_NOT_MEMBER);
	}

	/* ── API-040 편집 ───────────────────────────────────────── */

	@Test
	void 새_항목은_날짜마다_1부터_순서를_매기고_정한_도착_시각부터_이동_시간을_더해_계산한다() {
		ScheduleUpdateResponse response = update(
			new ItemEdit(null, placeA, DAY1, 5, DAY1_10AM, 60, true),
			newItem(placeB, DAY1, 9, 30));
		em.clear();

		ScheduleView schedule = response.schedule();
		assertThat(response.version()).isEqualTo(5);
		assertThat(schedule.startPoint()).isNull();
		assertThat(schedule.endPoint()).isNull();
		assertThat(items(schedule, DAY1))
			.extracting(ScheduleView.Item::roomPlaceId, ScheduleView.Item::order, ScheduleView.Item::arrivalAt,
				ScheduleView.Item::departureAt, ScheduleView.Item::fixed)
			.containsExactly(
				tuple(placeA, 1, DAY1_10AM, DAY1_10AM.plusSeconds(3600), true),
				tuple(placeB, 2, DAY1_10AM.plusSeconds(3600 + TRAVEL_MINUTES * 60),
					DAY1_10AM.plusSeconds(3600 + TRAVEL_MINUTES * 60 + 1800), false));
		ScheduleView.Day day = schedule.days().getFirst();
		assertThat(day.legs()).singleElement().satisfies(leg -> {
			assertThat(leg.fromItemId()).isEqualTo(day.items().get(0).id());
			assertThat(leg.toItemId()).isEqualTo(day.items().get(1).id());
			assertThat(leg.transportMode()).isEqualTo(TransportMode.CAR);
			assertThat(leg.travelMinutes()).isEqualTo(TRAVEL_MINUTES);
			assertThat(leg.source()).isEqualTo(FakeTravelTimeProvider.SOURCE);
		});
		assertThat(day.warnings()).isEmpty();
		// 다시 조회해도 같다.
		assertThat(scheduleService.getSchedule(OWNER, roomId).schedule()).isEqualTo(schedule);
	}

	@Test
	void 첫날_출발지와_마지막_날_도착지는_그날에만_구간이_생긴다() {
		// 일차 조건·지점은 초안 저장(API-039)이 채운다. 그 API 전이라 직접 넣는다.
		ScheduleOptions.NamedPoint station = new ScheduleOptions.NamedPoint("강릉역", new BigDecimal("37.7640000"),
			new BigDecimal("128.8990000"));
		TravelRoom room = em.find(TravelRoom.class, roomId);
		room.updateScheduleOptions(new ScheduleOptions(List.of(
			new ScheduleOptions.DayOption(DAY1, LocalTime.of(10, 0), LocalTime.of(18, 0)),
			new ScheduleOptions.DayOption(DAY2, LocalTime.of(9, 0), LocalTime.of(13, 0))),
			station, station, null, List.of()).toMap(), NOW);
		em.flush();

		ScheduleView schedule = update(newItem(placeA, DAY1, 1, 60), newItem(placeB, DAY2, 1, 60)).schedule();

		assertThat(schedule.startPoint()).isEqualTo(new ScheduleView.Point("강릉역", station.latitude(),
			station.longitude()));
		ScheduleView.Day first = schedule.days().get(0);
		ScheduleView.Day last = schedule.days().get(1);
		// 첫날: 출발지 → 첫 장소 구간만, 10:00 + 20분에 도착
		assertThat(first.legs()).extracting(ScheduleView.Leg::fromItemId, ScheduleView.Leg::toItemId)
			.containsExactly(tuple(null, first.items().getFirst().id()));
		assertThat(first.items().getFirst().arrivalAt()).isEqualTo(DAY1_10AM.plusSeconds(TRAVEL_MINUTES * 60));
		// 마지막 날: 출발 구간 없이 09:00 도착, 마지막 장소 → 도착지 구간만
		assertThat(last.legs()).extracting(ScheduleView.Leg::fromItemId, ScheduleView.Leg::toItemId)
			.containsExactly(tuple(last.items().getFirst().id(), null));
		assertThat(last.items().getFirst().arrivalAt()).isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
		assertThat(scheduleService.getSchedule(OWNER, roomId).schedule()).isEqualTo(schedule);
	}

	@Test
	void 항목을_모두_지우면_일차_조건이_있어도_schedule은_null이다() {
		TravelRoom room = em.find(TravelRoom.class, roomId);
		room.updateScheduleOptions(new ScheduleOptions(List.of(
			new ScheduleOptions.DayOption(DAY1, LocalTime.of(10, 0), LocalTime.of(18, 0))), null, null, null,
			List.of()).toMap(), NOW);
		em.flush();
		Long itemId = items(update(newItem(placeA, DAY1, 1, 60)).schedule(), DAY1).getFirst().id();

		ScheduleUpdateResponse response = scheduleService.update(OWNER, roomId,
			new ScheduleUpdateCommand(version(), List.of(), List.of(itemId)));

		assertThat(response.schedule()).isNull();
		assertThat(scheduleService.getSchedule(OWNER, roomId).schedule()).isNull();
	}

	@Test
	void 기준_시각이_없으면_도착_시각을_지어내지_않고_경고한다() {
		ScheduleView schedule = update(newItem(placeA, DAY1, 1, 60)).schedule();

		assertThat(items(schedule, DAY1)).singleElement().satisfies(item -> {
			assertThat(item.arrivalAt()).isNull();
			assertThat(item.departureAt()).isNull();
		});
		assertThat(schedule.days().getFirst().warnings()).containsExactly(ScheduleView.WARNING_ARRIVAL_TIME_UNKNOWN);
	}

	@Test
	void 이동_시간을_모르면_다음_도착_시각은_null이고_경고한다() {
		travelTimeProvider.willReturn(null);

		ScheduleView schedule = update(
			new ItemEdit(null, placeA, DAY1, 1, DAY1_10AM, 60, null),
			newItem(placeB, DAY1, 2, 30)).schedule();

		assertThat(items(schedule, DAY1)).extracting(ScheduleView.Item::arrivalAt)
			.containsExactly(DAY1_10AM, null);
		ScheduleView.Day day = schedule.days().getFirst();
		assertThat(day.legs()).singleElement().extracting(ScheduleView.Leg::travelMinutes).isNull();
		assertThat(day.warnings()).containsExactlyInAnyOrder(ScheduleView.WARNING_TRAVEL_TIME_UNAVAILABLE,
			ScheduleView.WARNING_ARRIVAL_TIME_UNKNOWN);
	}

	@Test
	void 기존_항목을_다른_날로_옮기면_두_날_모두_순서를_다시_매긴다() {
		ScheduleView before = update(newItem(placeA, DAY1, 1, 60), newItem(placeB, DAY1, 2, 60),
			newItem(placeC, DAY2, 1, 60)).schedule();
		Long movedId = items(before, DAY1).get(0).id();

		ScheduleView after = update(new ItemEdit(movedId, placeA, DAY2, 2, null, 90, null)).schedule();

		assertThat(items(after, DAY1)).extracting(ScheduleView.Item::roomPlaceId, ScheduleView.Item::order)
			.containsExactly(tuple(placeB, 1));
		assertThat(items(after, DAY2))
			.extracting(ScheduleView.Item::id, ScheduleView.Item::roomPlaceId, ScheduleView.Item::order,
				ScheduleView.Item::stayMinutes)
			.containsExactly(tuple(items(before, DAY2).get(0).id(), placeC, 1, 60), tuple(movedId, placeA, 2, 90));
	}

	@Test
	void 같은_후보를_여러_번_배치할_수_있고_지울_수_있다() {
		ScheduleView before = update(newItem(placeA, DAY1, 1, 60), newItem(placeA, DAY1, 2, 60)).schedule();
		Long first = items(before, DAY1).get(0).id();

		ScheduleView after = scheduleService.update(OWNER, roomId,
			new ScheduleUpdateCommand(version(), List.of(), List.of(first))).schedule();

		assertThat(items(after, DAY1)).extracting(ScheduleView.Item::roomPlaceId, ScheduleView.Item::order)
			.containsExactly(tuple(placeA, 1));
	}

	@Test
	void 바꿀_것이_없으면_version이_그대로다() {
		int version = version();

		ScheduleUpdateResponse response = scheduleService.update(OWNER, roomId,
			new ScheduleUpdateCommand(version, List.of(), List.of()));

		assertThat(response.version()).isEqualTo(version);
		assertThat(response.schedule()).isNull();
	}

	@Test
	void version이_다르면_C004() {
		assertError(() -> scheduleService.update(OWNER, roomId,
			new ScheduleUpdateCommand(version() - 1, List.of(newItem(placeA, DAY1, 1, 60)), List.of())),
			ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 같은_날_방문_순서가_겹치면_SC005() {
		assertError(() -> update(newItem(placeA, DAY1, 1, 60), newItem(placeB, DAY1, 1, 60)),
			ExceptionType.SCHEDULE_ORDER_DUPLICATED);

		update(newItem(placeA, DAY1, 1, 60));
		// 그대로 두는 기존 항목과 겹쳐도 SC005
		assertError(() -> update(newItem(placeB, DAY1, 1, 60)), ExceptionType.SCHEDULE_ORDER_DUPLICATED);
	}

	@Test
	void 체류_시간은_1분_단위로_1분부터_720분까지다() {
		assertError(() -> update(newItem(placeA, DAY1, 1, -10)), ExceptionType.SCHEDULE_STAY_NEGATIVE);
		assertError(() -> update(newItem(placeA, DAY1, 1, 0)), ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> update(newItem(placeA, DAY1, 1, 721)), ExceptionType.NOT_ALLOWED_VALUE);
		assertThat(items(update(newItem(placeB, DAY2, 1, 720)).schedule(), DAY2)).singleElement()
			.extracting(ScheduleView.Item::stayMinutes).isEqualTo(720);
		assertError(() -> update(newItem(placeA, DAY1, 0, 60)), ExceptionType.NOT_ALLOWED_VALUE);

		ScheduleView schedule = update(new ItemEdit(null, placeA, DAY1, 1, DAY1_10AM, 47, null)).schedule();

		assertThat(items(schedule, DAY1)).singleElement().satisfies(item -> {
			assertThat(item.stayMinutes()).isEqualTo(47);
			assertThat(item.departureAt()).isEqualTo(DAY1_10AM.plusSeconds(47 * 60));
		});
	}

	@Test
	void 여행_날짜_밖이거나_도착_시각이_다른_날이면_C012() {
		assertError(() -> update(newItem(placeA, DAY2.plusDays(1), 1, 60)), ExceptionType.INVALID_DATE_RANGE);
		assertError(() -> update(new ItemEdit(null, placeA, DAY2, 1, DAY1_10AM, 60, null)),
			ExceptionType.INVALID_DATE_RANGE);
	}

	@Test
	void 뺀_후보는_새로_배치할_수_없지만_이미_배치한_항목은_고칠_수_있다() {
		Long itemId = items(update(newItem(placeA, DAY1, 1, 60)).schedule(), DAY1).getFirst().id();
		roomPlaceService.remove(OWNER, roomId, placeA, version());

		assertError(() -> update(newItem(placeA, DAY1, 2, 60)), ExceptionType.NOT_ALLOWED_VALUE);
		ScheduleView schedule = update(new ItemEdit(itemId, placeA, DAY1, 1, null, 120, null)).schedule();

		assertThat(items(schedule, DAY1)).singleElement()
			.extracting(ScheduleView.Item::roomPlaceId, ScheduleView.Item::stayMinutes)
			.containsExactly(placeA, 120);
	}

	@Test
	void 다른_방_후보는_RO009_다른_방_항목은_C003() {
		Long otherRoom = roomService.create(OWNER, new RoomCreateCommand("다른 방", DAY1, DAY2, TransportMode.CAR))
			.id();
		Long otherPlace = roomPlaceService.addCustomPlace(OWNER, otherRoom, new CustomPlaceCreateCommand("남의 후보",
			new BigDecimal("37.5"), new BigDecimal("127.0"), null)).roomPlace().id();
		Long otherItem = items(scheduleService.update(OWNER, otherRoom, new ScheduleUpdateCommand(2,
			List.of(newItem(otherPlace, DAY1, 1, 60)), List.of())).schedule(), DAY1).getFirst().id();

		assertError(() -> update(newItem(otherPlace, DAY1, 1, 60)), ExceptionType.ROOM_PLACE_NOT_FOUND);
		assertError(() -> update(new ItemEdit(otherItem, placeA, DAY1, 1, null, 60, null)),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> scheduleService.update(OWNER, roomId,
			new ScheduleUpdateCommand(version(), List.of(), List.of(otherItem))), ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 날짜가_없는_방에는_일정을_넣을_수_없다() {
		Long undated = roomService.create(OWNER, new RoomCreateCommand("미정", null, null, TransportMode.CAR)).id();
		Long place = roomPlaceService.addCustomPlace(OWNER, undated, new CustomPlaceCreateCommand("후보",
			new BigDecimal("37.5"), new BigDecimal("127.0"), null)).roomPlace().id();

		assertError(() -> scheduleService.update(OWNER, undated,
			new ScheduleUpdateCommand(2, List.of(newItem(place, DAY1, 1, 60)), List.of())),
			ExceptionType.INVALID_DATE_RANGE);
	}

	/* ── API-022·023 연결 ──────────────────────────────────── */

	@Test
	void 여행방_상세에_일정이_들어가고_뺀_후보의_항목도_보인다() {
		update(newItem(placeA, DAY1, 1, 60));
		roomPlaceService.remove(OWNER, roomId, placeA, version());

		var detail = roomService.getRoom(FRIEND, roomId);

		assertThat(detail.places()).extracting(place -> place.id()).doesNotContain(placeA);
		assertThat(items(detail.schedule(), DAY1)).extracting(ScheduleView.Item::roomPlaceId)
			.containsExactly(placeA);
	}

	@Test
	void 날짜를_줄이면_미리보기는_빠질_항목만_알려_주고_저장하면_지운다() {
		update(newItem(placeA, DAY1, 1, 60), newItem(placeB, DAY2, 1, 60));
		int version = version();

		RoomUpdateResponse preview = roomService.update(OWNER, roomId,
			new RoomUpdateCommand(version, null, true, DAY1, DAY1, null, true));
		assertThat(preview.applied()).isFalse();
		assertThat(preview.version()).isEqualTo(version);
		assertThat(preview.schedule()).isNull();
		assertThat(preview.affectedItems())
			.extracting(RoomUpdateResponse.AffectedItem::roomPlaceId, RoomUpdateResponse.AffectedItem::name,
				RoomUpdateResponse.AffectedItem::visitDate)
			.containsExactly(tuple(placeB, "안목해변", DAY2));

		RoomUpdateResponse saved = roomService.update(OWNER, roomId,
			new RoomUpdateCommand(version, null, true, DAY1, DAY1, null, false));
		em.clear();

		assertThat(saved.applied()).isTrue();
		assertThat(saved.version()).isEqualTo(version + 1);
		assertThat(saved.affectedItems()).hasSize(1);
		assertThat(saved.schedule().days()).extracting(ScheduleView.Day::date).containsExactly(DAY1);
		assertThat(scheduleService.getSchedule(OWNER, roomId).schedule()).isEqualTo(saved.schedule());
		// 후보 장소는 그대로다.
		assertThat(roomService.getRoom(OWNER, roomId).places()).hasSize(3);
	}

	@Test
	void 일정이_있는_방의_날짜는_지울_수_없다() {
		update(newItem(placeA, DAY1, 1, 60));

		assertError(() -> roomService.update(OWNER, roomId,
			new RoomUpdateCommand(version(), null, true, null, null, null, true)), ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 이동_수단을_바꾸면_남은_일정을_새_이동_수단으로_다시_계산한다() {
		update(new ItemEdit(null, placeA, DAY1, 1, DAY1_10AM, 60, null), newItem(placeB, DAY1, 2, 60));
		travelTimeProvider.willReturn(45);

		RoomUpdateResponse response = roomService.update(OWNER, roomId,
			new RoomUpdateCommand(version(), null, false, null, null, TransportMode.TRANSIT, false));

		assertThat(travelTimeProvider.calls()).containsOnly(TransportMode.TRANSIT);
		ScheduleView.Day day = response.schedule().days().getFirst();
		assertThat(day.legs()).singleElement().satisfies(leg -> {
			assertThat(leg.transportMode()).isEqualTo(TransportMode.TRANSIT);
			assertThat(leg.travelMinutes()).isEqualTo(45);
		});
		// 일차 시작 시각이 없으면 지금 첫 도착 시각을 기준으로 다시 계산한다.
		assertThat(day.items()).extracting(ScheduleView.Item::arrivalAt)
			.containsExactly(DAY1_10AM, DAY1_10AM.plusSeconds((60 + 45) * 60));
	}
}
