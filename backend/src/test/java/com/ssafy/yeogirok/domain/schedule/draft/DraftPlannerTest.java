package com.ssafy.yeogirok.domain.schedule.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.draft.DraftPlanner.Candidate;
import com.ssafy.yeogirok.domain.schedule.draft.DraftPlanner.Output;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.domain.schedule.travel.TravelTimeProvider;
import com.ssafy.yeogirok.global.exception.BusinessException;
import com.ssafy.yeogirok.global.exception.ExceptionType;

/** 초안 날짜 배정·순서·식사·시간 넘김 규칙. 길찾기는 직선 거리 1km 당 2분으로 둔다. */
class DraftPlannerTest {

	static final LocalDate DAY1 = LocalDate.of(2026, 11, 8);
	static final LocalDate DAY2 = DAY1.plusDays(1);
	/** 강릉 쪽과 속초 쪽(약 60km 떨어짐). */
	static final double GANGNEUNG_LAT = 37.75;
	static final double SOKCHO_LAT = 38.20;
	static final double LNG = 128.90;

	final List<GeoPoint[]> calls = new ArrayList<>();
	final TravelTimeProvider distanceProvider = (from, to, mode) -> {
		calls.add(new GeoPoint[] {from, to});
		int minutes = (int)Math.ceil(DraftPlanner.distanceKm(from,
			new double[] {to.latitude().doubleValue(), to.longitude().doubleValue()}) * 2);
		return TravelLeg.found(minutes, BigDecimal.ONE, "TEST", Instant.EPOCH);
	};

	private static GeoPoint at(double lat, double lngOffset) {
		return new GeoPoint(BigDecimal.valueOf(lat), BigDecimal.valueOf(LNG + lngOffset));
	}

	private static Candidate place(int id, GeoPoint location, int stay) {
		return new Candidate(id, location, false, stay, null, null);
	}

	private static Candidate food(int id, GeoPoint location) {
		return new Candidate(id, location, true, 60, null, null);
	}

	private static DayOption day(LocalDate date, int from, int to) {
		return new DayOption(date, LocalTime.of(from, 0), LocalTime.of(to, 0));
	}

	private Output plan(List<DayOption> days, List<Candidate> candidates) {
		return DraftPlanner.plan(new DraftPlanner.Input(days, null, null, candidates, TransportMode.CAR),
			distanceProvider);
	}

	/** itemId → 도착 시각(분). 시작 시각부터 순서대로 이동·체류를 더한다. */
	private Map<Integer, Integer> arrivals(Output output, List<DayOption> days, List<Candidate> candidates) {
		Map<Integer, Candidate> byId = new HashMap<>();
		candidates.forEach(c -> byId.put(c.itemId(), c));
		Map<Integer, Integer> arrivals = new HashMap<>();
		for (DayOption day : days) {
			int clock = day.startTime().toSecondOfDay() / 60;
			GeoPoint previous = null;
			for (int id : output.orders().get(day.date())) {
				Candidate candidate = byId.get(id);
				if (previous != null) {
					clock += distanceProvider.estimate(previous, candidate.location(), TransportMode.CAR).travelMinutes();
				}
				arrivals.put(id, clock);
				clock += candidate.stayMinutes();
				previous = candidate.location();
			}
		}
		return arrivals;
	}

	@Test
	void 가까운_장소끼리_같은_날로_묶는다() {
		List<Candidate> candidates = List.of(
			place(1, at(GANGNEUNG_LAT, 0), 60), place(2, at(SOKCHO_LAT, 0), 60),
			place(3, at(GANGNEUNG_LAT, 0.01), 60), place(4, at(SOKCHO_LAT, 0.01), 60));

		Output output = plan(List.of(day(DAY1, 9, 18), day(DAY2, 9, 18)), candidates);

		assertThat(output.unassigned()).isEmpty();
		List<Integer> first = output.orders().get(DAY1);
		List<Integer> second = output.orders().get(DAY2);
		assertThat(first).hasSize(2);
		assertThat(second).hasSize(2);
		assertThat(List.of(List.copyOf(first).stream().sorted().toList(), second.stream().sorted().toList()))
			.containsExactlyInAnyOrder(List.of(1, 3), List.of(2, 4));
	}

	@Test
	void 가까운_장소가_한쪽에_몰려도_날짜마다_시간에_맞게_고르게_나눈다() {
		List<Candidate> candidates = new ArrayList<>();
		for (int i = 1; i <= 7; i++) {
			candidates.add(place(i, at(GANGNEUNG_LAT, i * 0.01), 60));
		}
		candidates.add(place(8, at(SOKCHO_LAT, 0), 60));

		Output output = plan(List.of(day(DAY1, 10, 20), day(DAY2, 10, 20)), candidates);

		assertThat(output.unassigned()).isEmpty();
		assertThat(output.orders().get(DAY1)).hasSize(4);
		assertThat(output.orders().get(DAY2)).hasSize(4);
	}

	@Test
	void 맛집은_점심은_11시에서_14시_저녁은_17시에서_20시이면서_점심_5시간_뒤에_도착하게_놓는다() {
		List<DayOption> days = List.of(day(DAY1, 9, 21));
		List<Candidate> candidates = List.of(
			place(1, at(GANGNEUNG_LAT, 0), 120), food(2, at(GANGNEUNG_LAT, 0.01)), place(3, at(GANGNEUNG_LAT, 0.02), 120),
			place(4, at(GANGNEUNG_LAT, 0.03), 120), food(5, at(GANGNEUNG_LAT, 0.04)), place(6, at(GANGNEUNG_LAT, 0.05), 60));

		Output output = plan(days, candidates);
		Map<Integer, Integer> arrivals = arrivals(output, days, candidates);

		assertThat(output.unassigned()).isEmpty();
		int lunch = Math.min(arrivals.get(2), arrivals.get(5));
		int dinner = Math.max(arrivals.get(2), arrivals.get(5));
		assertThat(lunch).isBetween(11 * 60, 14 * 60);
		assertThat(dinner).isBetween(17 * 60, 20 * 60);
		assertThat(dinner - lunch).isGreaterThanOrEqualTo(5 * 60);
	}

	@Test
	void 맛집이_모자라면_모든_날의_점심을_먼저_채운다() {
		// 맛집 둘이 모두 강릉 쪽에 있어도 속초 쪽 날에 하나를 옮겨 점심을 채운다.
		List<Candidate> candidates = List.of(
			food(1, at(GANGNEUNG_LAT, 0)), food(2, at(GANGNEUNG_LAT, 0.01)), place(3, at(GANGNEUNG_LAT, 0.02), 60),
			place(4, at(SOKCHO_LAT, 0), 60), place(5, at(SOKCHO_LAT, 0.01), 60));

		Output output = plan(List.of(day(DAY1, 9, 21), day(DAY2, 9, 21)), candidates);

		assertThat(output.orders().get(DAY1)).containsAnyOf(1, 2);
		assertThat(output.orders().get(DAY2)).containsAnyOf(1, 2);
	}

	@Test
	void 순서_고정_항목은_받은_날짜와_순서_자리에_둔다() {
		List<Candidate> candidates = List.of(
			place(1, at(GANGNEUNG_LAT, 0), 60), place(2, at(GANGNEUNG_LAT, 0.01), 60),
			new Candidate(3, at(GANGNEUNG_LAT, 0.05), false, 60, DAY2, 1), place(4, at(GANGNEUNG_LAT, 0.02), 60));

		Output output = plan(List.of(day(DAY1, 9, 18), day(DAY2, 9, 18)), candidates);

		assertThat(output.orders().get(DAY2)).first().isEqualTo(3);
	}

	@Test
	void 종료_시각을_넘기면_고정하지_않은_항목을_빼서_미배치로_둔다() {
		List<DayOption> days = List.of(day(DAY1, 9, 12));
		List<Candidate> candidates = List.of(
			place(1, at(GANGNEUNG_LAT, 0), 60), place(2, at(GANGNEUNG_LAT, 0.01), 60),
			place(3, at(GANGNEUNG_LAT, 0.02), 60), place(4, at(GANGNEUNG_LAT, 0.03), 60));

		Output output = plan(days, candidates);

		assertThat(output.unassigned()).isNotEmpty();
		assertThat(output.orders().get(DAY1).size() + output.unassigned().size()).isEqualTo(4);
		Map<Integer, Integer> arrivals = arrivals(output, days, candidates);
		int lastId = output.orders().get(DAY1).getLast();
		assertThat(arrivals.get(lastId) + 60).isLessThanOrEqualTo(12 * 60);
	}

	@Test
	void 고정_항목만으로도_넘치고_이동_시간을_모두_알면_SC002() {
		List<Candidate> candidates = List.of(
			new Candidate(1, at(GANGNEUNG_LAT, 0), false, 120, DAY1, 1),
			new Candidate(2, at(GANGNEUNG_LAT, 0.01), false, 120, DAY1, 2));

		assertThatThrownBy(() -> plan(List.of(day(DAY1, 9, 12)), candidates))
			.isInstanceOf(BusinessException.class)
			.extracting("exceptionType").isEqualTo(ExceptionType.SCHEDULE_UNSATISFIABLE);
	}

	@Test
	void 이동_시간을_모르면_어림값으로_고르고_실패하지_않는다() {
		TravelTimeProvider unknown = (from, to, mode) -> TravelLeg.unavailable("TEST", "DOWN", Instant.EPOCH);
		List<Candidate> candidates = List.of(
			place(1, at(GANGNEUNG_LAT, 0), 60), place(2, at(GANGNEUNG_LAT, 0.01), 60), place(3, at(SOKCHO_LAT, 0), 60));

		Output output = DraftPlanner.plan(new DraftPlanner.Input(List.of(day(DAY1, 9, 18)), null, null, candidates,
			TransportMode.TRANSIT), unknown);

		assertThat(output.orders().get(DAY1)).containsExactlyInAnyOrder(1, 2, 3);
	}

	@Test
	void 하루_10곳도_순서를_모두_비교해_빨리_끝난다() {
		List<Candidate> candidates = new ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			candidates.add(i % 4 == 0 ? food(i, at(GANGNEUNG_LAT + i * 0.003, i * 0.004))
				: place(i, at(GANGNEUNG_LAT + i * 0.003, i * 0.004), 30));
		}

		Output output = assertTimeoutPreemptively(Duration.ofSeconds(15),
			() -> plan(List.of(day(DAY1, 9, 21)), candidates));

		assertThat(output.orders().get(DAY1)).hasSize(10);
	}

	@Test
	void 같은_날_장소_사이_이동_시간만_구한다() {
		List<Candidate> candidates = List.of(
			place(1, at(GANGNEUNG_LAT, 0), 60), place(2, at(GANGNEUNG_LAT, 0.01), 60),
			place(3, at(SOKCHO_LAT, 0), 60), place(4, at(SOKCHO_LAT, 0.01), 60));

		plan(List.of(day(DAY1, 9, 18), day(DAY2, 9, 18)), candidates);

		// 날마다 2곳 → 순서쌍 2개씩. 다른 날 장소끼리는 부르지 않는다.
		assertThat(calls).hasSize(4);
	}
}
