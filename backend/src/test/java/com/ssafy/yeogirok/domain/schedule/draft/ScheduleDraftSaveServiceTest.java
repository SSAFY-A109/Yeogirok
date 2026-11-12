package com.ssafy.yeogirok.domain.schedule.draft;

import static com.ssafy.yeogirok.domain.room.RoomTestConfig.LEGAL_DONG;
import static com.ssafy.yeogirok.domain.room.RoomTestConfig.NOW;
import static com.ssafy.yeogirok.domain.room.RoomTestConfig.TRAVEL_MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.ssafy.yeogirok.domain.job.JobError;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.room.event.RoomChange;
import com.ssafy.yeogirok.domain.room.event.RoomChangedEvent;
import com.ssafy.yeogirok.domain.room.service.RoomPlaceService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.domain.schedule.LastApplied;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.schedule.dto.DraftSaveCommand;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleResponse;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleUpdateCommand;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;
import com.ssafy.yeogirok.support.FakeTravelTimeProvider;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;
import com.ssafy.yeogirok.support.TestUsers;

/** API-039 최종 저장(검증·재계산·저장·재전송). 초안은 API-037 로 만든다. 길찾기는 구간마다 20분. */
@DataJpaTest
@Import(DraftTestConfig.class)
@RecordApplicationEvents
class ScheduleDraftSaveServiceTest {

	static final Long OWNER = 7201L;
	static final LocalDate DAY1 = LocalDate.of(2026, 11, 8);
	static final LocalDate DAY2 = DAY1.plusDays(1);
	static final String KEY = "save-key-1";

	@Autowired
	ScheduleDraftSaveService saveService;
	@Autowired
	ScheduleDraftService draftService;
	@Autowired
	ScheduleDraftStore draftStore;
	@Autowired
	ScheduleDraftLock draftLock;
	@Autowired
	ScheduleService scheduleService;
	@Autowired
	RoomService roomService;
	@Autowired
	RoomPlaceService roomPlaceService;
	@Autowired
	FakeTravelTimeProvider travelTimeProvider;
	@Autowired
	FixedLegalDongResolver legalDongResolver;
	@Autowired
	TestEntityManager em;
	@Autowired
	MutableClock clock;
	@Autowired
	ApplicationEvents events;

	Long roomId;
	List<Long> places;
	ScheduleDraft draft;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock.set(NOW);
		legalDongResolver.willReturn(LEGAL_DONG);
		travelTimeProvider.willReturn(TRAVEL_MINUTES);
		TestUsers.insertActive(em.getEntityManager(), OWNER);
		roomId = roomService.create(OWNER, new RoomCreateCommand("강릉 여행", DAY1, DAY2, TransportMode.CAR)).id();
		places = List.of(addPlace("경포대", 0), addPlace("초당순두부", 0.001), addPlace("오죽헌", 0.002),
			addPlace("안목해변", 0.003));
		String draftKey = draftService.create(OWNER, roomId, new DraftCreateCommand(version(), places,
			List.of(new DayOption(DAY1, LocalTime.of(9, 0), LocalTime.of(18, 0)),
				new DayOption(DAY2, LocalTime.of(10, 0), LocalTime.of(18, 0))), null, null, Map.of(), List.of()))
			.draftKey();
		draft = draftStore.get(roomId, draftKey);
	}

	private Long addPlace(String name, double offset) {
		return roomPlaceService.addCustomPlace(OWNER, roomId, new CustomPlaceCreateCommand(name,
			new BigDecimal("37.75").add(BigDecimal.valueOf(offset)), new BigDecimal("128.87"), null)).roomPlace().id();
	}

	private int version() {
		return scheduleService.getSchedule(OWNER, roomId).version();
	}

	/** 초안 배치 그대로의 저장 요청. */
	private DraftSaveCommand asIs() {
		List<DraftSaveCommand.Day> days = new ArrayList<>();
		for (DayOption day : draft.days()) {
			days.add(new DraftSaveCommand.Day(day.date(), day.startTime(), day.endTime(), draft.items().stream()
				.filter(item -> day.date().equals(item.visitDate()))
				.sorted(Comparator.comparing(DraftItem::order))
				.map(item -> new DraftSaveCommand.Item(item.itemId(), item.roomPlaceId(), item.order(),
					item.stayMinutes(), item.fixed()))
				.toList()));
		}
		List<Integer> unassigned = draft.items().stream().filter(item -> !item.placed()).map(DraftItem::itemId)
			.toList();
		return new DraftSaveCommand(draft.draftKey(), draft.draftVersion(), draft.baseVersion(), days, unassigned,
			null, null);
	}

	/** 첫날 첫 항목 하나를 바꾼 요청. */
	private DraftSaveCommand withFirstItem(UnaryOperator<DraftSaveCommand.Item> change) {
		DraftSaveCommand base = asIs();
		List<DraftSaveCommand.Day> days = new ArrayList<>(base.days());
		DraftSaveCommand.Day first = days.getFirst();
		List<DraftSaveCommand.Item> items = new ArrayList<>(first.items());
		items.set(0, change.apply(items.getFirst()));
		days.set(0, new DraftSaveCommand.Day(first.date(), first.startTime(), first.endTime(), items));
		return new DraftSaveCommand(base.draftKey(), base.draftVersion(), base.baseVersion(), days,
			base.unassignedItemIds(), null, null);
	}

	private DraftSaveCommand withDays(List<DraftSaveCommand.Day> days, List<Integer> unassigned) {
		DraftSaveCommand base = asIs();
		return new DraftSaveCommand(base.draftKey(), base.draftVersion(), base.baseVersion(), days, unassigned,
			null, null);
	}

	private ScheduleResponse save(DraftSaveCommand command) {
		return saveService.save(OWNER, roomId, command, KEY);
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 초안_배치대로_저장하면_확정_일정이_되고_방_version이_1_오른다() {
		int before = version();
		NamedPoint station = new NamedPoint("강릉역", new BigDecimal("37.764"), new BigDecimal("128.899"));
		DraftSaveCommand base = asIs();
		DraftSaveCommand command = new DraftSaveCommand(base.draftKey(), base.draftVersion(), base.baseVersion(),
			base.days(), base.unassignedItemIds(), station, null);

		ScheduleResponse response = save(command);
		em.clear();

		assertThat(response.version()).isEqualTo(before + 1);
		ScheduleView schedule = response.schedule();
		assertThat(schedule.startPoint().name()).isEqualTo("강릉역");
		assertThat(schedule.days()).extracting(ScheduleView.Day::date).containsExactly(DAY1, DAY2);
		assertThat(schedule.days().get(1).startTime()).isEqualTo(LocalTime.of(10, 0));
		assertThat(schedule.days().stream().mapToInt(day -> day.items().size()).sum()).isEqualTo(4);
		// 첫날은 출발지에서 20분 이동해 09:20 도착
		assertThat(schedule.days().getFirst().items().getFirst().arrivalAt())
			.isEqualTo(ZonedDateTime.of(DAY1, LocalTime.of(9, 20), ApiDateTime.ZONE).toInstant());
		assertThat(scheduleService.getSchedule(OWNER, roomId)).isEqualTo(response);
		LastApplied applied = LastApplied.from(em.find(TravelRoom.class, roomId).getScheduleOptions());
		assertThat(applied.draftKey()).isEqualTo(draft.draftKey());
		assertThat(applied.resultVersion()).isEqualTo(before + 1);
		assertThat(applied.requestKeyHash()).isNotEqualTo(KEY);
	}

	@Test
	void 날짜를_옮기고_체류_시간을_바꾸고_항목을_빼면_그대로_다시_계산해_저장한다() {
		DraftSaveCommand base = asIs();
		List<DraftSaveCommand.Item> all = base.days().stream().flatMap(day -> day.items().stream()).toList();
		DraftSaveCommand.Item moved = all.get(0);
		DraftSaveCommand.Item dropped = all.get(1);
		List<DraftSaveCommand.Item> rest = all.subList(2, all.size());
		List<DraftSaveCommand.Day> days = List.of(
			new DraftSaveCommand.Day(DAY1, LocalTime.of(9, 0), LocalTime.of(18, 0), List.of(
				new DraftSaveCommand.Item(rest.get(0).itemId(), rest.get(0).roomPlaceId(), 1, 90, true))),
			new DraftSaveCommand.Day(DAY2, LocalTime.of(10, 0), LocalTime.of(18, 0), List.of(
				new DraftSaveCommand.Item(rest.get(1).itemId(), rest.get(1).roomPlaceId(), 1, 60, false),
				new DraftSaveCommand.Item(moved.itemId(), moved.roomPlaceId(), 2, 30, false))));

		ScheduleView schedule = save(withDays(days, List.of(dropped.itemId()))).schedule();

		ScheduleView.Day first = schedule.days().get(0);
		ScheduleView.Day second = schedule.days().get(1);
		assertThat(first.items()).singleElement().satisfies(item -> {
			assertThat(item.stayMinutes()).isEqualTo(90);
			assertThat(item.fixed()).isTrue();
		});
		assertThat(second.items()).extracting(ScheduleView.Item::roomPlaceId)
			.containsExactly(rest.get(1).roomPlaceId(), moved.roomPlaceId());
		assertThat(second.items().get(1).arrivalAt())
			.isEqualTo(ZonedDateTime.of(DAY2, LocalTime.of(11, 20), ApiDateTime.ZONE).toInstant());
		assertThat(schedule.unassignedPlaces()).extracting(ScheduleView.Unassigned::roomPlaceId)
			.containsExactly(dropped.roomPlaceId());
	}

	@Test
	void 초안을_만든_뒤_일정이_바뀌면_C004() {
		scheduleService.update(OWNER, roomId, new ScheduleUpdateCommand(version(),
			List.of(new ScheduleUpdateCommand.ItemEdit(null, places.get(0), DAY1, 1, 60, null)), List.of()));

		// 040 으로 방 version 이 바뀌어 초안의 baseVersion 이 낡았다.
		assertError(() -> save(asIs()), ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 기존_확정_일정은_요청한_배치로_모두_바뀐다() {
		scheduleService.update(OWNER, roomId, new ScheduleUpdateCommand(version(), List.of(
			new ScheduleUpdateCommand.ItemEdit(null, places.get(0), DAY1, 1, 60, null),
			new ScheduleUpdateCommand.ItemEdit(null, places.get(0), DAY2, 1, 60, null)), List.of()));
		String draftKey = draftService.create(OWNER, roomId, new DraftCreateCommand(version(), places, draft.days(),
			null, null, Map.of(), List.of())).draftKey();
		draft = draftStore.get(roomId, draftKey);

		ScheduleView schedule = save(asIs()).schedule();

		assertThat(schedule.days().stream().flatMap(day -> day.items().stream()).map(ScheduleView.Item::roomPlaceId))
			.containsExactlyInAnyOrderElementsOf(places);
	}

	@Test
	void 최종_저장하면_재계산_필요가_꺼진다() {
		// 후보를 바꾸면 방 version 이 바뀌어 초안이 낡으므로, 켜진 상태만 직접 만든다.
		em.getEntityManager().createNativeQuery(
			"UPDATE travel_rooms SET schedule_needs_recalculation = TRUE WHERE id = :id")
			.setParameter("id", roomId).executeUpdate();
		em.clear();

		save(asIs());
		em.flush();
		em.clear();

		assertThat(em.find(TravelRoom.class, roomId).isScheduleNeedsRecalculation()).isFalse();
	}

	@Test
	void 같은_요청을_다시_보내면_저장한_결과를_돌려주고_version이_그대로다() {
		ScheduleResponse first = save(asIs());

		ScheduleResponse again = saveService.save(OWNER, roomId, asIs(), "another-key");

		assertThat(again).isEqualTo(first);
		assertThat(version()).isEqualTo(first.version());
	}

	@Test
	void 같은_초안을_다른_내용으로_다시_적용하면_C004() {
		save(asIs());

		assertError(() -> save(withFirstItem(item -> new DraftSaveCommand.Item(item.itemId(), item.roomPlaceId(),
			item.order(), item.stayMinutes() + 1, item.fixed()))), ExceptionType.VERSION_MISMATCH);
	}

	/* ── 요청 확인 ─────────────────────────────────────────── */

	@Test
	void 항목이_빠지거나_겹치거나_원본에_없거나_후보가_바뀌면_C003() {
		DraftSaveCommand base = asIs();
		List<DraftSaveCommand.Day> withoutFirst = new ArrayList<>(base.days());
		DraftSaveCommand.Day first = withoutFirst.getFirst();
		withoutFirst.set(0, new DraftSaveCommand.Day(first.date(), first.startTime(), first.endTime(),
			first.items().subList(1, first.items().size())));
		assertError(() -> save(withDays(withoutFirst, base.unassignedItemIds())), ExceptionType.NOT_ALLOWED_VALUE);

		int firstId = first.items().getFirst().itemId();
		List<Integer> duplicated = new ArrayList<>(base.unassignedItemIds());
		duplicated.add(firstId);
		assertError(() -> save(withDays(base.days(), duplicated)), ExceptionType.NOT_ALLOWED_VALUE);

		assertError(() -> save(withFirstItem(item -> new DraftSaveCommand.Item(99, item.roomPlaceId(), item.order(),
			item.stayMinutes(), item.fixed()))), ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> save(withFirstItem(item -> new DraftSaveCommand.Item(item.itemId(),
			places.stream().filter(id -> !id.equals(item.roomPlaceId())).findFirst().orElseThrow(), item.order(),
			item.stayMinutes(), item.fixed()))), ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 순서가_겹치면_SC005_비면_C003_체류_시간이_음수면_SC006() {
		DraftSaveCommand base = asIs();
		DraftSaveCommand.Day day = base.days().stream().filter(d -> d.items().size() >= 2).findFirst().orElseThrow();
		List<DraftSaveCommand.Item> items = new ArrayList<>(day.items());
		DraftSaveCommand.Item second = items.get(1);
		items.set(1, new DraftSaveCommand.Item(second.itemId(), second.roomPlaceId(), 1, second.stayMinutes(),
			second.fixed()));
		assertError(() -> save(replaceDay(base, day, items)), ExceptionType.SCHEDULE_ORDER_DUPLICATED);

		items.set(1, new DraftSaveCommand.Item(second.itemId(), second.roomPlaceId(), 5, second.stayMinutes(),
			second.fixed()));
		assertError(() -> save(replaceDay(base, day, items)), ExceptionType.NOT_ALLOWED_VALUE);

		assertError(() -> save(withFirstItem(item -> new DraftSaveCommand.Item(item.itemId(), item.roomPlaceId(),
			item.order(), -5, item.fixed()))), ExceptionType.SCHEDULE_STAY_NEGATIVE);
		assertError(() -> save(withFirstItem(item -> new DraftSaveCommand.Item(item.itemId(), item.roomPlaceId(),
			item.order(), 721, item.fixed()))), ExceptionType.NOT_ALLOWED_VALUE);
	}

	private DraftSaveCommand replaceDay(DraftSaveCommand base, DraftSaveCommand.Day target,
		List<DraftSaveCommand.Item> items) {
		List<DraftSaveCommand.Day> days = base.days().stream()
			.map(day -> day == target ? new DraftSaveCommand.Day(day.date(), day.startTime(), day.endTime(), items)
				: day)
			.toList();
		return withDays(days, base.unassignedItemIds());
	}

	@Test
	void 종료_시각을_넘기면_SC002이고_아무것도_바뀌지_않는다() {
		int before = version();
		DraftSaveCommand base = asIs();
		List<DraftSaveCommand.Day> days = base.days().stream()
			.map(day -> new DraftSaveCommand.Day(day.date(), day.startTime(), day.startTime().plusMinutes(30),
				day.items()))
			.toList();

		assertError(() -> save(withDays(days, base.unassignedItemIds())), ExceptionType.SCHEDULE_UNSATISFIABLE);
		assertThat(version()).isEqualTo(before);
		assertThat(scheduleService.getSchedule(OWNER, roomId).schedule()).isNull();
	}

	@Test
	void 초안_버전이_다르면_C004_없는_초안은_SC001() {
		DraftSaveCommand base = asIs();

		assertError(() -> save(new DraftSaveCommand(base.draftKey(), base.draftVersion() + 1, base.baseVersion(),
			base.days(), base.unassignedItemIds(), null, null)), ExceptionType.VERSION_MISMATCH);
		assertError(() -> save(new DraftSaveCommand("no-such-draft", base.draftVersion(), base.baseVersion(),
			base.days(), base.unassignedItemIds(), null, null)), ExceptionType.SCHEDULE_DRAFT_NOT_FOUND);
	}

	@Test
	void 계산_중인_초안은_SC003_실패한_초안은_SC004() {
		draftStore.save(draft.recalculating("550e8400-e29b-41d4-a716-446655440009", NOW, DraftTestConfig.RUN_TIMEOUT));
		assertError(() -> save(asIs()), ExceptionType.SCHEDULE_DRAFT_IN_PROGRESS);

		draftStore.save(draft.failed(new JobError("C014", "실패"), NOW));
		assertError(() -> save(asIs()), ExceptionType.SCHEDULE_DRAFT_FAILED);
	}

	@Test
	void 같은_초안을_다른_요청이_잠그고_있으면_SC003() {
		String token = draftLock.acquire(draft.draftKey());
		try {
			assertError(() -> save(asIs()), ExceptionType.SCHEDULE_DRAFT_IN_PROGRESS);
		} finally {
			draftLock.release(draft.draftKey(), token);
		}
		assertThat(save(asIs()).schedule()).isNotNull();
	}

	/* ── 실시간 알림(ROOM_UPDATED) 이벤트 ─────────────────────── */

	@Test
	void 최종_저장하면_SCHEDULE_변경을_알리고_같은_요청_재전송은_알리지_않는다() {
		events.clear();
		DraftSaveCommand command = asIs();

		ScheduleResponse saved = save(command);
		save(command);

		assertThat(events.stream(RoomChangedEvent.class))
			.containsExactly(RoomChangedEvent.of(roomId, OWNER, saved.version(), RoomChange.SCHEDULE));
	}
}
