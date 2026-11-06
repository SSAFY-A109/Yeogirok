package com.ssafy.yeogirok.domain.schedule.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.job.Job;
import com.ssafy.yeogirok.domain.job.JobProperties;
import com.ssafy.yeogirok.domain.job.JobStatus;
import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.ScheduleProperties;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;
import com.ssafy.yeogirok.support.MutableClock;
import com.ssafy.yeogirok.support.TestRedis;

import tools.jackson.databind.json.JsonMapper;

/** 초안 저장·조회·상태와 초안 잠금. 실제 Redis. */
class ScheduleDraftStoreTest {

	static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
	static final Duration RUN_TIMEOUT = Duration.ofMinutes(2);
	static final LocalDate DAY = LocalDate.of(2026, 11, 8);
	static final String KEY = "550e8400-e29b-41d4-a716-446655440001";

	MutableClock clock;
	ScheduleDraftStore store;
	ScheduleDraftLock lock;

	@BeforeEach
	void setUp() {
		TestRedis.flushAll();
		clock = new MutableClock(NOW);
		store = new ScheduleDraftStore(TestRedis.template(), JsonMapper.builder().build(),
			new ScheduleProperties(Duration.ofHours(24)), clock);
		lock = new ScheduleDraftLock(TestRedis.template(),
			new JobProperties(Duration.ofHours(24), RUN_TIMEOUT, 1));
	}

	private static ScheduleDraft started() {
		return ScheduleDraft.start(KEY, "550e8400-e29b-41d4-a716-446655440000", 7L, 1L, 3, TransportMode.TRANSIT,
			List.of(new DayOption(DAY, LocalTime.of(9, 0), LocalTime.of(18, 0))),
			new NamedPoint("강릉역", new BigDecimal("37.7640000"), new BigDecimal("128.8990000")), null,
			List.of(DraftItem.unassigned(1, 401L, 60, false), DraftItem.unassigned(2, 401L, 30, true)), NOW,
			RUN_TIMEOUT);
	}

	private static ScheduleDraft computed(ScheduleDraft draft) {
		TravelLeg leg = TravelLeg.found(15, new BigDecimal("1200.00"), "KAKAO_TRANSIT", NOW);
		return draft.computed(draft.days(), draft.startPoint(), draft.endPoint(), List.of(
			new DraftItem(1, 401L, DAY, 1, 60, false, NOW, NOW.plusSeconds(3600), leg),
			draft.items().get(1)), null, NOW);
	}

	@Test
	void 저장한_초안을_그대로_읽는다() {
		ScheduleDraft draft = computed(started());
		store.save(draft);

		assertThat(store.find(KEY)).contains(draft);
		assertThat(draft.status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(draft.draftVersion()).isEqualTo(1);
	}

	@Test
	void 다른_방이거나_없는_초안은_SC001() {
		store.save(started());

		assertThat(store.get(7L, KEY).draftKey()).isEqualTo(KEY);
		assertThatThrownBy(() -> store.get(8L, KEY)).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.SCHEDULE_DRAFT_NOT_FOUND);
		assertThatThrownBy(() -> store.get(7L, "550e8400-e29b-41d4-a716-446655440099"))
			.extracting("exceptionType").isEqualTo(ExceptionType.SCHEDULE_DRAFT_NOT_FOUND);
	}

	@Test
	void 키는_만든_시각부터_보관_기간만큼_남고_다시_저장해도_늘지_않는다() {
		ScheduleDraft draft = started();
		store.save(draft);
		clock.advance(Duration.ofHours(10));
		store.save(computed(draft));

		Long ttlSeconds = TestRedis.template().getExpire("schedule-draft:" + KEY);
		assertThat(ttlSeconds).isBetween(Duration.ofHours(14).toSeconds() - 5, Duration.ofHours(14).toSeconds());
	}

	@Test
	void 처음_계산이_기한을_넘기면_실패로_보인다() {
		store.save(started());
		clock.advance(RUN_TIMEOUT.plusSeconds(1));

		ScheduleDraft draft = store.get(7L, KEY);
		assertThat(draft.status()).isEqualTo(JobStatus.FAILED);
		assertThat(draft.error()).isEqualTo(Job.TIMED_OUT);
	}

	@Test
	void 다시_계산이_기한을_넘기면_마지막_성공으로_보인다() {
		ScheduleDraft done = computed(started());
		store.save(done.recalculating("550e8400-e29b-41d4-a716-446655440002", NOW, RUN_TIMEOUT));
		assertThat(store.get(7L, KEY).status()).isEqualTo(JobStatus.RUNNING);

		clock.advance(RUN_TIMEOUT.plusSeconds(1));
		ScheduleDraft draft = store.get(7L, KEY);
		assertThat(draft.status()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(draft.draftVersion()).isEqualTo(1);
		assertThat(draft.items()).isEqualTo(done.items());
	}

	@Test
	void 같은_초안은_한_번에_한_요청만_잠근다() {
		String token = lock.acquire(KEY);

		assertThatThrownBy(() -> lock.acquire(KEY)).isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.SCHEDULE_DRAFT_IN_PROGRESS);
		lock.release(KEY, "someone-else");
		assertThatThrownBy(() -> lock.acquire(KEY)).isInstanceOf(BusinessException.class);
		lock.release(KEY, token);
		lock.release(KEY, lock.acquire(KEY));
	}
}
