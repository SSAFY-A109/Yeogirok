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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.job.dto.JobResponse;
import com.ssafy.yeogirok.domain.job.service.JobService;
import com.ssafy.yeogirok.domain.room.dto.CustomPlaceCreateCommand;
import com.ssafy.yeogirok.domain.room.dto.RoomCreateCommand;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.room.service.RoomInvitationService;
import com.ssafy.yeogirok.domain.room.service.RoomPlaceService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.schedule.dto.DraftCreateCommand.FixedItem;
import com.ssafy.yeogirok.domain.schedule.dto.DraftJobResponse;
import com.ssafy.yeogirok.domain.schedule.dto.DraftResponse;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleService;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.global.response.ApiDateTime;
import com.ssafy.yeogirok.support.FakeTravelTimeProvider;
import com.ssafy.yeogirok.support.FixedLegalDongResolver;
import com.ssafy.yeogirok.support.HoldableExecutor;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;
import com.ssafy.yeogirok.support.TestUsers;

/** API-037 초안 생성(요청 확인·작업·초안 저장). 작업은 바로 실행된다(DraftTestConfig). 길찾기는 구간마다 20분. */
@DataJpaTest
@Import(DraftTestConfig.class)
class ScheduleDraftServiceTest {

	static final Long OWNER = 7101L;
	static final Long FRIEND = 7102L;
	static final Long STRANGER = 7103L;
	static final LocalDate DAY1 = LocalDate.of(2026, 11, 8);
	static final LocalDate DAY2 = DAY1.plusDays(1);

	@Autowired
	ScheduleDraftService draftService;
	@Autowired
	ScheduleDraftStore draftStore;
	@Autowired
	JobService jobService;
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
	@Autowired
	HoldableExecutor executor;

	Long roomId;
	List<Long> places;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		executor.reset();
		clock.set(NOW);
		legalDongResolver.willReturn(LEGAL_DONG);
		travelTimeProvider.willReturn(TRAVEL_MINUTES);
		TestUsers.insertActive(em.getEntityManager(), OWNER, FRIEND, STRANGER);
		roomId = roomService.create(OWNER, new RoomCreateCommand("강릉 여행", DAY1, DAY2, TransportMode.CAR)).id();
		roomInvitationService.accept(FRIEND,
			roomInvitationService.getOrCreateInvitation(OWNER, roomId).inviteToken());
		places = List.of(addPlace("경포대", 0), addPlace("초당순두부", 0.001), addPlace("오죽헌", 0.002),
			addPlace("안목해변", 0.003));
		markFood(places.get(1));
	}

	private Long addPlace(String name, double offset) {
		return roomPlaceService.addCustomPlace(OWNER, roomId, new CustomPlaceCreateCommand(name,
			new BigDecimal("37.75").add(BigDecimal.valueOf(offset)), new BigDecimal("128.87"), null)).roomPlace().id();
	}

	private void markFood(Long roomPlaceId) {
		em.getEntityManager().createNativeQuery("""
			UPDATE places SET category_code = 'FOOD'
			WHERE id = (SELECT place_id FROM room_places WHERE id = :id)
			""").setParameter("id", roomPlaceId).executeUpdate();
		em.clear();
	}

	private int version() {
		return scheduleService.getSchedule(OWNER, roomId).version();
	}

	private static List<DayOption> nineToSix() {
		return List.of(new DayOption(DAY1, LocalTime.of(9, 0), LocalTime.of(18, 0)),
			new DayOption(DAY2, LocalTime.of(9, 0), LocalTime.of(18, 0)));
	}

	private DraftCreateCommand command(List<Long> placeIds, List<DayOption> days, Map<Long, Integer> stay,
		List<FixedItem> fixed) {
		return new DraftCreateCommand(version(), placeIds, days, null, null, stay, fixed);
	}

	private DraftJobResponse create(DraftCreateCommand command) {
		return draftService.create(OWNER, roomId, command);
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 초안을_만들면_작업이_끝나고_모든_항목을_날짜와_순서와_시각으로_배치한다() {
		DraftJobResponse response = create(command(places, nineToSix(), Map.of(places.get(0), 45), List.of()));

		assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
		JobResponse job = jobService.get(OWNER, response.jobId());
		assertThat(job.status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(job.result()).containsEntry("type", "SCHEDULE_DRAFT").containsEntry("draftKey", response.draftKey());

		ScheduleDraft draft = draftStore.get(roomId, response.draftKey());
		assertThat(draft.status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(draft.draftVersion()).isEqualTo(1);
		assertThat(draft.baseVersion()).isEqualTo(version());
		assertThat(draft.items()).extracting(DraftItem::itemId).containsExactly(1, 2, 3, 4);
		assertThat(draft.items()).extracting(DraftItem::roomPlaceId).containsExactlyElementsOf(places);
		assertThat(draft.items()).allSatisfy(item -> {
			assertThat(item.placed()).isTrue();
			assertThat(item.arrivalAt()).isNotNull();
		});
		assertThat(draft.items().getFirst().stayMinutes()).isEqualTo(45);
		assertThat(draft.items().get(1).stayMinutes()).isEqualTo(ScheduleDraftService.DEFAULT_STAY_MINUTES);
		// 날마다 1부터 이어지는 순서, 첫 항목은 시작 시각(09:00)에 도착하고 다음 항목은 20분 이동 뒤에 도착한다.
		for (LocalDate date : List.of(DAY1, DAY2)) {
			List<DraftItem> day = draft.items().stream().filter(item -> date.equals(item.visitDate()))
				.sorted((a, b) -> a.order() - b.order()).toList();
			if (day.isEmpty()) {
				continue;
			}
			assertThat(day).extracting(DraftItem::order)
				.containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, day.size()).boxed().toList());
			assertThat(day.getFirst().arrivalAt())
				.isEqualTo(ZonedDateTime.of(date, LocalTime.of(9, 0), ApiDateTime.ZONE).toInstant());
			for (int i = 1; i < day.size(); i++) {
				assertThat(day.get(i).arrivalAt())
					.isEqualTo(day.get(i - 1).departureAt().plusSeconds(TRAVEL_MINUTES * 60));
			}
		}
	}

	@Test
	void 초안은_확정_일정과_방_version을_바꾸지_않는다() {
		int before = version();

		create(command(places, nineToSix(), Map.of(), List.of()));

		assertThat(version()).isEqualTo(before);
		assertThat(scheduleService.getSchedule(OWNER, roomId).schedule()).isNull();
	}

	@Test
	void 이어받은_고정_항목은_그_날짜와_순서에_두고_같은_후보는_더_만들지_않는다() {
		List<FixedItem> fixed = List.of(new FixedItem(places.get(2), DAY2, 1, 90, true),
			new FixedItem(places.get(2), DAY1, 1, 30, false));

		DraftJobResponse response = create(command(places, nineToSix(), Map.of(), fixed));

		ScheduleDraft draft = draftStore.get(roomId, response.draftKey());
		assertThat(draft.items()).hasSize(5);
		DraftItem first = draft.items().get(0);
		assertThat(first.itemId()).isEqualTo(1);
		assertThat(first.roomPlaceId()).isEqualTo(places.get(2));
		assertThat(first.visitDate()).isEqualTo(DAY2);
		assertThat(first.order()).isEqualTo(1);
		assertThat(first.stayMinutes()).isEqualTo(90);
		assertThat(first.fixed()).isTrue();
		assertThat(draft.items().get(1).visitDate()).isEqualTo(DAY1);
		assertThat(draft.items().stream().filter(item -> item.roomPlaceId().equals(places.get(2)))).hasSize(2);
	}

	@Test
	void 출발지와_도착지를_초안_조건에_남긴다() {
		NamedPoint station = new NamedPoint("강릉역", new BigDecimal("37.764"), new BigDecimal("128.899"));

		DraftJobResponse response = draftService.create(OWNER, roomId,
			new DraftCreateCommand(version(), places, nineToSix(), station, station, Map.of(), List.of()));

		ScheduleDraft draft = draftStore.get(roomId, response.draftKey());
		assertThat(draft.startPoint()).isEqualTo(station);
		assertThat(draft.endPoint()).isEqualTo(station);
		assertThat(draft.endLeg()).isNotNull();
		assertThat(draft.items().stream().filter(item -> DAY1.equals(item.visitDate()) && item.order() == 1)
			.findFirst().orElseThrow().legFromPrevious()).isNotNull();
	}

	@Test
	void 계산이_조건을_만족할_수_없으면_작업과_초안이_SC002로_실패한다() {
		List<FixedItem> fixed = List.of(new FixedItem(places.get(0), DAY1, 1, 600, true),
			new FixedItem(places.get(1), DAY1, 2, 600, true));

		DraftJobResponse response = create(command(places, nineToSix(), Map.of(), fixed));

		JobResponse job = jobService.get(OWNER, response.jobId());
		assertThat(job.status()).isEqualTo(JobStatus.FAILED);
		assertThat(job.error().code()).isEqualTo("SC002");
		assertThat(job.retryable()).isFalse();
		ScheduleDraft draft = draftStore.get(roomId, response.draftKey());
		assertThat(draft.status()).isEqualTo(JobStatus.FAILED);
		assertThat(draft.error().code()).isEqualTo("SC002");
	}

	@Test
	void 작업은_요청자만_보고_방을_나가면_J003() {
		DraftJobResponse response = create(command(places, nineToSix(), Map.of(), List.of()));

		assertError(() -> jobService.get(FRIEND, response.jobId()), ExceptionType.JOB_NOT_REQUESTER);
		roomService.leave(OWNER, roomId);
		assertError(() -> jobService.get(OWNER, response.jobId()), ExceptionType.JOB_RESOURCE_FORBIDDEN);
	}

	/* ── API-038 조회 ───────────────────────────────────────── */

	@Test
	void 초안을_조회하면_모든_일차와_itemId로_이은_구간과_조건을_보여_준다() {
		NamedPoint station = new NamedPoint("강릉역", new BigDecimal("37.764"), new BigDecimal("128.899"));
		DraftJobResponse created = draftService.create(OWNER, roomId,
			new DraftCreateCommand(version(), places, nineToSix(), station, null, Map.of(), List.of()));

		DraftResponse response = draftService.get(FRIEND, roomId, created.draftKey());

		assertThat(response.status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(response.draftVersion()).isEqualTo(1);
		assertThat(response.transportMode()).isEqualTo(TransportMode.CAR);
		assertThat(response.travelDataSource()).isEqualTo(FakeTravelTimeProvider.SOURCE);
		assertThat(response.error()).isNull();
		assertThat(response.conditions().days()).extracting(DraftResponse.DayCondition::date)
			.containsExactly(DAY1, DAY2);
		assertThat(response.conditions().startPoint().name()).isEqualTo("강릉역");
		DraftResponse.Schedule schedule = response.schedule();
		assertThat(schedule.days()).extracting(DraftResponse.Day::date).containsExactly(DAY1, DAY2);
		assertThat(schedule.days().stream().mapToInt(day -> day.items().size()).sum()
			+ schedule.unassignedPlaces().size()).isEqualTo(4);
		DraftResponse.Day first = schedule.days().getFirst();
		// 첫날은 출발지 → 첫 항목 구간부터 항목 사이 구간까지 itemId 로 잇는다.
		assertThat(first.legs()).extracting(ScheduleView.Leg::fromItemId).first().isNull();
		assertThat(first.legs()).extracting(ScheduleView.Leg::toItemId)
			.containsExactlyElementsOf(first.items().stream().map(item -> (long)item.itemId()).toList());
		assertThat(schedule.startPoint().name()).isEqualTo("강릉역");
	}

	@Test
	void 실패한_초안은_schedule_없이_오류를_보여_준다() {
		List<FixedItem> fixed = List.of(new FixedItem(places.get(0), DAY1, 1, 600, true),
			new FixedItem(places.get(1), DAY1, 2, 600, true));
		DraftJobResponse created = create(command(places, nineToSix(), Map.of(), fixed));

		DraftResponse response = draftService.get(OWNER, roomId, created.draftKey());

		assertThat(response.status()).isEqualTo(JobStatus.FAILED);
		assertThat(response.schedule()).isNull();
		assertThat(response.travelDataSource()).isNull();
		assertThat(response.error().code()).isEqualTo("SC002");
	}

	@Test
	void 대기_제한_시간이_지나_시작하지_못한_초안은_계산하지_않고_실패로_남는다() {
		executor.hold();
		DraftJobResponse created = create(command(places, nineToSix(), Map.of(), List.of()));

		clock.advance(DraftTestConfig.QUEUE_TIMEOUT.plusSeconds(1));
		assertThat(jobService.get(OWNER, created.jobId()).status()).isEqualTo(JobStatus.FAILED);
		executor.runHeld();

		assertThat(jobService.get(OWNER, created.jobId()).status()).isEqualTo(JobStatus.FAILED);
		DraftResponse response = draftService.get(OWNER, roomId, created.draftKey());
		assertThat(response.status()).isEqualTo(JobStatus.FAILED);
		assertThat(response.error().code()).isEqualTo("C014");
		assertThat(response.schedule()).isNull();
	}

	@Test
	void 대기한_시간은_초안_계산의_실행_제한_시간에_넣지_않는다() {
		executor.hold();
		DraftJobResponse created = create(command(places, nineToSix(), Map.of(), List.of()));

		clock.advance(DraftTestConfig.QUEUE_TIMEOUT.minusSeconds(1));
		executor.runHeld();

		assertThat(jobService.get(OWNER, created.jobId()).status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(draftService.get(OWNER, roomId, created.draftKey()).status()).isEqualTo(JobStatus.SUCCEEDED);
	}

	@Test
	void 참여자가_아니면_RO002_다른_방이나_없는_초안은_SC001() {
		DraftJobResponse created = create(command(places, nineToSix(), Map.of(), List.of()));
		Long otherRoom = roomService.create(OWNER, new RoomCreateCommand("다른 방", DAY1, DAY2, TransportMode.CAR))
			.id();

		assertError(() -> draftService.get(STRANGER, roomId, created.draftKey()), ExceptionType.ROOM_NOT_MEMBER);
		assertError(() -> draftService.get(OWNER, otherRoom, created.draftKey()),
			ExceptionType.SCHEDULE_DRAFT_NOT_FOUND);
		assertError(() -> draftService.get(OWNER, roomId, "no-such-draft"), ExceptionType.SCHEDULE_DRAFT_NOT_FOUND);
	}

	/* ── 요청 확인 ─────────────────────────────────────────── */

	@Test
	void 참여자가_아니면_RO002_version이_다르면_C004() {
		assertError(() -> draftService.create(STRANGER, roomId, command(places, nineToSix(), Map.of(), List.of())),
			ExceptionType.ROOM_NOT_MEMBER);
		assertError(() -> create(new DraftCreateCommand(version() - 1, places, nineToSix(), null, null, Map.of(),
			List.of())), ExceptionType.VERSION_MISMATCH);
	}

	@Test
	void 후보가_3곳_미만이거나_현재_후보_전부가_아니면_C003() {
		assertError(() -> create(command(places.subList(0, 3), nineToSix(), Map.of(), List.of())),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> create(command(List.of(places.get(0), places.get(0), places.get(1), places.get(2),
			places.get(3)), nineToSix(), Map.of(), List.of())), ExceptionType.NOT_ALLOWED_VALUE);
		roomPlaceService.remove(OWNER, roomId, places.get(3), version());
		roomPlaceService.remove(OWNER, roomId, places.get(2), version());
		assertError(() -> create(command(places.subList(0, 2), nineToSix(), Map.of(), List.of())),
			ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 일차는_여행의_모든_날짜를_중복_없이_담고_시작이_종료보다_앞이어야_한다_C012() {
		assertError(() -> create(command(places, List.of(nineToSix().getFirst()), Map.of(), List.of())),
			ExceptionType.INVALID_DATE_RANGE);
		assertError(() -> create(command(places, List.of(nineToSix().getFirst(), nineToSix().getFirst()), Map.of(),
			List.of())), ExceptionType.INVALID_DATE_RANGE);
		assertError(() -> create(command(places, List.of(nineToSix().getFirst(),
			new DayOption(DAY2, LocalTime.of(18, 0), LocalTime.of(9, 0))), Map.of(), List.of())),
			ExceptionType.INVALID_DATE_RANGE);
		assertError(() -> create(command(places, List.of(nineToSix().getFirst(),
			new DayOption(DAY2.plusDays(1), LocalTime.of(9, 0), LocalTime.of(18, 0))), Map.of(), List.of())),
			ExceptionType.INVALID_DATE_RANGE);
	}

	@Test
	void 체류_시간은_1분부터_720분_음수는_SC006() {
		assertError(() -> create(command(places, nineToSix(), Map.of(places.get(0), -1), List.of())),
			ExceptionType.SCHEDULE_STAY_NEGATIVE);
		assertError(() -> create(command(places, nineToSix(), Map.of(places.get(0), 0), List.of())),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> create(command(places, nineToSix(), Map.of(places.get(0), 721), List.of())),
			ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> create(command(places, nineToSix(), Map.of(),
			List.of(new FixedItem(places.get(0), DAY1, 1, 0, true)))), ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 날짜가_없는_방은_C003() {
		Long undated = roomService.create(OWNER, new RoomCreateCommand("미정", null, null, TransportMode.CAR)).id();

		int undatedVersion = scheduleService.getSchedule(OWNER, undated).version();

		assertError(() -> draftService.create(OWNER, undated, new DraftCreateCommand(undatedVersion, List.of(),
			nineToSix(), null, null, Map.of(), List.of())), ExceptionType.NOT_ALLOWED_VALUE);
	}
}
