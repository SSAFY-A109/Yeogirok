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
import java.util.Comparator;
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
import com.ssafy.yeogirok.domain.room.service.RoomPlaceService;
import com.ssafy.yeogirok.domain.room.service.RoomService;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.dto.DraftAdjustCommand;
import com.ssafy.yeogirok.domain.schedule.dto.DraftAdjustCommand.PointUpdate;
import com.ssafy.yeogirok.domain.schedule.dto.DraftCreateCommand;
import com.ssafy.yeogirok.domain.schedule.dto.DraftJobResponse;
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

/** API-084 초안 조정·재계산. 초안은 API-037 로 만든다. 작업은 바로 실행된다. 길찾기는 구간마다 20분. */
@DataJpaTest
@Import(DraftTestConfig.class)
class ScheduleDraftAdjustServiceTest {

	static final Long OWNER = 7301L;
	static final LocalDate DAY1 = LocalDate.of(2026, 11, 8);
	static final LocalDate DAY2 = DAY1.plusDays(1);

	@Autowired
	ScheduleDraftAdjustService adjustService;
	@Autowired
	ScheduleDraftService draftService;
	@Autowired
	ScheduleDraftStore draftStore;
	@Autowired
	ScheduleDraftLock draftLock;
	@Autowired
	JobService jobService;
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
	HoldableExecutor executor;

	Long roomId;
	ScheduleDraft draft;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		executor.reset();
		clock.set(NOW);
		legalDongResolver.willReturn(LEGAL_DONG);
		travelTimeProvider.willReturn(TRAVEL_MINUTES);
		TestUsers.insertActive(em.getEntityManager(), OWNER);
		roomId = roomService.create(OWNER, new RoomCreateCommand("강릉 여행", DAY1, DAY2, TransportMode.CAR)).id();
		List<Long> places = List.of(addPlace("경포대", 0), addPlace("초당순두부", 0.001), addPlace("오죽헌", 0.002),
			addPlace("안목해변", 0.003));
		String draftKey = draftService.create(OWNER, roomId, new DraftCreateCommand(version(), places,
			List.of(new DayOption(DAY1, LocalTime.of(9, 0), LocalTime.of(18, 0)),
				new DayOption(DAY2, LocalTime.of(9, 0), LocalTime.of(18, 0))), null, null, Map.of(), List.of()))
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

	private DraftItem placed(int index) {
		return draft.items().stream().filter(DraftItem::placed)
			.sorted(Comparator.comparing(DraftItem::visitDate).thenComparing(DraftItem::order))
			.toList().get(index);
	}

	private static DraftAdjustCommand.Item move(DraftItem item, LocalDate date, int order, int stay) {
		return new DraftAdjustCommand.Item(item.itemId(), item.roomPlaceId(), date, order, stay, null);
	}

	private DraftAdjustCommand command(List<DraftAdjustCommand.Item> items, List<Integer> removes) {
		return new DraftAdjustCommand(draft.baseVersion(), draft.draftVersion(), items, removes, null, null, null);
	}

	private DraftJobResponse adjust(DraftAdjustCommand command) {
		return adjustService.adjust(OWNER, roomId, draft.draftKey(), command);
	}

	private ScheduleDraft current() {
		return draftStore.get(roomId, draft.draftKey());
	}

	private List<DraftItem> day(ScheduleDraft value, LocalDate date) {
		return value.items().stream().filter(item -> date.equals(item.visitDate()))
			.sorted(Comparator.comparing(DraftItem::order)).toList();
	}

	private static void assertError(Runnable call, ExceptionType type) {
		assertThatThrownBy(call::run).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(type);
	}

	@Test
	void 항목을_다른_날_맨_앞으로_옮기고_체류_시간을_바꾸면_순서를_다시_매기고_다시_계산한다() {
		DraftItem target = day(draft, DAY1).getLast();
		// 맨 앞에 넣으려면 그날 항목의 순서도 함께 보낸다(그대로 둔 항목과 순서가 겹치면 SC005).
		List<DraftAdjustCommand.Item> edits = new java.util.ArrayList<>();
		edits.add(move(target, DAY2, 1, 90));
		day(draft, DAY2).forEach(item -> edits.add(move(item, DAY2, item.order() + 1, item.stayMinutes())));

		DraftJobResponse response = adjust(command(edits, List.of()));

		assertThat(response.draftKey()).isEqualTo(draft.draftKey());
		assertThat(jobService.get(OWNER, response.jobId()).status()).isEqualTo(JobStatus.SUCCEEDED);
		ScheduleDraft after = current();
		assertThat(after.draftVersion()).isEqualTo(2);
		assertThat(after.jobId()).isEqualTo(response.jobId());
		List<DraftItem> second = day(after, DAY2);
		assertThat(second.getFirst().itemId()).isEqualTo(target.itemId());
		assertThat(second.getFirst().stayMinutes()).isEqualTo(90);
		assertThat(second).extracting(DraftItem::order)
			.containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, second.size()).boxed().toList());
		assertThat(second.get(1).arrivalAt()).isEqualTo(ZonedDateTime.of(DAY2, LocalTime.of(9, 0), ApiDateTime.ZONE)
			.toInstant().plusSeconds((90 + TRAVEL_MINUTES) * 60));
		assertThat(day(after, DAY1)).extracting(DraftItem::order)
			.containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, day(after, DAY1).size()).boxed()
				.toList());
	}

	@Test
	void 대기열에서_기다리는_동안에도_초안_잠금은_대기와_실행_제한을_합친_만큼_유지한다() {
		executor.hold();
		adjust(command(List.of(move(placed(0), placed(0).visitDate(), placed(0).order(), 90)), List.of()));

		Long ttlSeconds = TestRedis.template().getExpire("schedule-draft-lock:" + draft.draftKey());
		long lifetime = DraftTestConfig.QUEUE_TIMEOUT.plus(DraftTestConfig.RUN_TIMEOUT).toSeconds();
		assertThat(ttlSeconds).isBetween(lifetime - 5, lifetime);
	}

	@Test
	void 대기_제한_시간이_지나_시작하지_못한_재계산은_마지막_성공으로_돌아가고_잠금을_푼다() {
		executor.hold();
		DraftItem target = placed(0);
		DraftJobResponse response = adjust(command(List.of(move(target, target.visitDate(), target.order(), 90)),
			List.of()));

		clock.advance(DraftTestConfig.QUEUE_TIMEOUT.plusSeconds(1));
		executor.runHeld();

		assertThat(jobService.get(OWNER, response.jobId()).status()).isEqualTo(JobStatus.FAILED);
		ScheduleDraft after = current();
		assertThat(after.status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(after.draftVersion()).isEqualTo(draft.draftVersion());
		assertThat(after.items()).isEqualTo(draft.items());
		String token = draftLock.acquire(draft.draftKey());
		draftLock.release(draft.draftKey(), token);
	}

	@Test
	void 항목을_빼면_같은_itemId로_미배치가_되고_다시_보내면_배치한다() {
		DraftItem target = placed(0);
		adjust(command(List.of(), List.of(target.itemId())));
		ScheduleDraft removed = current();
		assertThat(removed.items().stream().filter(item -> item.itemId() == target.itemId()).findFirst()
			.orElseThrow().placed()).isFalse();
		draft = removed;

		adjust(command(List.of(move(target, DAY2, 100, 60)), List.of()));

		assertThat(day(current(), DAY2).getLast().itemId()).isEqualTo(target.itemId());
		assertThat(current().draftVersion()).isEqualTo(3);
	}

	@Test
	void 종료_시각을_넘기면_고정하지_않은_마지막_항목부터_미배치로_옮긴다() {
		DraftItem first = day(draft, DAY1).getFirst();

		adjust(command(List.of(move(first, DAY1, 1, 520)), List.of()));

		ScheduleDraft after = current();
		assertThat(day(after, DAY1)).extracting(DraftItem::itemId).containsExactly(first.itemId());
		assertThat(after.items().stream().filter(item -> !item.placed())).isNotEmpty();
	}

	@Test
	void 고정_항목만으로도_넘치면_작업이_SC002로_실패하고_마지막_성공_초안이_남는다() {
		DraftItem first = day(draft, DAY1).getFirst();

		DraftJobResponse response = adjust(command(List.of(new DraftAdjustCommand.Item(first.itemId(),
			first.roomPlaceId(), DAY1, 1, 600, true)), List.of()));

		JobResponse job = jobService.get(OWNER, response.jobId());
		assertThat(job.status()).isEqualTo(JobStatus.FAILED);
		assertThat(job.error().code()).isEqualTo("SC002");
		ScheduleDraft after = current();
		assertThat(after.status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(after.draftVersion()).isEqualTo(1);
		assertThat(after.items()).isEqualTo(draft.items());
		// 잠금이 풀려 다시 조정할 수 있다.
		adjust(command(List.of(), List.of()));
		assertThat(current().draftVersion()).isEqualTo(2);
	}

	@Test
	void 출발지를_정하고_일차_조건을_바꾸고_도착지를_해제할_수_있다() {
		NamedPoint station = new NamedPoint("강릉역", new BigDecimal("37.764"), new BigDecimal("128.899"));
		List<DayOption> days = List.of(new DayOption(DAY1, LocalTime.of(10, 0), LocalTime.of(20, 0)),
			new DayOption(DAY2, LocalTime.of(9, 0), LocalTime.of(18, 0)));

		adjust(new DraftAdjustCommand(draft.baseVersion(), draft.draftVersion(), List.of(), List.of(), days,
			new PointUpdate(station), new PointUpdate(null)));

		ScheduleDraft after = current();
		assertThat(after.startPoint()).isEqualTo(station);
		assertThat(after.endPoint()).isNull();
		assertThat(after.days().getFirst().startTime()).isEqualTo(LocalTime.of(10, 0));
		assertThat(day(after, DAY1).getFirst().legFromPrevious()).isNotNull();
		assertThat(day(after, DAY1).getFirst().arrivalAt()).isEqualTo(
			ZonedDateTime.of(DAY1, LocalTime.of(10, 20), ApiDateTime.ZONE).toInstant());
	}

	/* ── 요청 확인 ─────────────────────────────────────────── */

	@Test
	void 그대로_둔_항목과_순서가_겹치면_SC005() {
		List<DraftItem> first = day(draft, DAY1);
		DraftItem other = day(draft, DAY2).getFirst();

		assertError(() -> adjust(command(List.of(move(other, DAY1, first.getFirst().order(), 60)), List.of())),
			ExceptionType.SCHEDULE_ORDER_DUPLICATED);
	}

	@Test
	void 초안_항목이_아니거나_후보를_바꾸거나_날짜_밖이면_C003_C012() {
		DraftItem item = placed(0);

		assertError(() -> adjust(command(List.of(new DraftAdjustCommand.Item(99, item.roomPlaceId(), DAY1, 1, 60,
			null)), List.of())), ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> adjust(command(List.of(new DraftAdjustCommand.Item(item.itemId(), item.roomPlaceId() + 1000,
			DAY1, 1, 60, null)), List.of())), ExceptionType.NOT_ALLOWED_VALUE);
		assertError(() -> adjust(command(List.of(move(item, DAY2.plusDays(1), 1, 60)), List.of())),
			ExceptionType.INVALID_DATE_RANGE);
		assertError(() -> adjust(command(List.of(move(item, DAY1, 1, -1)), List.of())),
			ExceptionType.SCHEDULE_STAY_NEGATIVE);
		assertError(() -> adjust(command(List.of(move(item, DAY1, 1, 60)), List.of(item.itemId()))),
			ExceptionType.NOT_ALLOWED_VALUE);
	}

	@Test
	void 버전이_다르면_C004_없는_초안은_SC001() {
		assertError(() -> adjust(new DraftAdjustCommand(draft.baseVersion(), draft.draftVersion() + 1, List.of(),
			List.of(), null, null, null)), ExceptionType.VERSION_MISMATCH);
		assertError(() -> adjustService.adjust(OWNER, roomId, "no-such-draft", command(List.of(), List.of())),
			ExceptionType.SCHEDULE_DRAFT_NOT_FOUND);
	}

	@Test
	void 다른_요청이_초안을_잠그고_있으면_SC003() {
		String token = draftLock.acquire(draft.draftKey());
		try {
			assertError(() -> adjust(command(List.of(), List.of())), ExceptionType.SCHEDULE_DRAFT_IN_PROGRESS);
		} finally {
			draftLock.release(draft.draftKey(), token);
		}
		assertThat(current().status()).isEqualTo(JobStatus.SUCCEEDED);
	}
}
