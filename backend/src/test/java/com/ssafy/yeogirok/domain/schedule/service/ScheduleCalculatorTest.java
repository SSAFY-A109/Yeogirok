package com.ssafy.yeogirok.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.UnassignedPlace;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleCalculator.PlannedItem;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleCalculator.Result;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.support.FakeTravelTimeProvider;

/** 일정 시각 계산 규칙과 일정 옵션 JSON 변환. */
class ScheduleCalculatorTest {

	static final LocalDate DAY = LocalDate.of(2026, 11, 5);
	/** DAY 09:00 Asia/Seoul. */
	static final Instant NINE = Instant.parse("2026-11-05T00:00:00Z");
	static final GeoPoint SPOT = new GeoPoint(new BigDecimal("37.75"), new BigDecimal("128.87"));
	static final NamedPoint HOTEL = new NamedPoint("숙소", new BigDecimal("37.70"), new BigDecimal("128.80"));

	final FakeTravelTimeProvider travel = new FakeTravelTimeProvider(20);

	private static PlannedItem item(int order, int stay) {
		return new PlannedItem(DAY, order, stay, null, SPOT);
	}

	private static ScheduleOptions options(DayOption... days) {
		return new ScheduleOptions("NORMAL", List.of(days), List.of());
	}

	@Test
	void 일차_시작_시각부터_출발_지점_구간을_더해_차례로_계산하고_도착_지점_구간도_구한다() {
		ScheduleOptions options = options(
			new DayOption(DAY, LocalTime.of(9, 0), LocalTime.of(18, 0), HOTEL, HOTEL, null));

		Result result = ScheduleCalculator.calculate(List.of(item(2, 30), item(1, 60)), options, Map.of(),
			TransportMode.CAR, travel);

		// order 1(입력 두 번째): 09:00 + 20분 이동 → 09:20 도착, 10:20 출발
		assertThat(result.timings().get(1).arrivalAt()).isEqualTo(NINE.plusSeconds(20 * 60));
		assertThat(result.timings().get(1).departureAt()).isEqualTo(NINE.plusSeconds(80 * 60));
		// order 2: 10:20 + 20분 → 10:40 도착, 11:10 출발
		assertThat(result.timings().get(0).arrivalAt()).isEqualTo(NINE.plusSeconds(100 * 60));
		assertThat(result.timings().get(0).departureAt()).isEqualTo(NINE.plusSeconds(130 * 60));
		assertThat(result.timings()).allSatisfy(timing -> assertThat(timing.legFromPrevious()).isNotNull());
		assertThat(result.endLegs().get(DAY).travelMinutes()).isEqualTo(20);
		assertThat(travel.calls()).hasSize(3);
	}

	@Test
	void 출발_지점이_없으면_첫_항목은_이동_없이_기준_시각에_도착한다() {
		ScheduleOptions options = options(new DayOption(DAY, LocalTime.of(9, 0), LocalTime.of(18, 0), null, null,
			null));

		Result result = ScheduleCalculator.calculate(List.of(item(1, 60)), options, Map.of(), TransportMode.CAR,
			travel);

		assertThat(result.timings().getFirst().arrivalAt()).isEqualTo(NINE);
		assertThat(result.timings().getFirst().legFromPrevious()).isNull();
		assertThat(result.endLegs()).isEmpty();
	}

	@Test
	void 일차_조건이_없으면_지금_첫_도착_시각을_기준으로_쓰고_그것도_없으면_null() {
		Result withFallback = ScheduleCalculator.calculate(List.of(item(1, 60)), ScheduleOptions.EMPTY,
			Map.of(DAY, NINE), TransportMode.CAR, travel);
		Result without = ScheduleCalculator.calculate(List.of(item(1, 60)), ScheduleOptions.EMPTY, Map.of(),
			TransportMode.CAR, travel);

		assertThat(withFallback.timings().getFirst().arrivalAt()).isEqualTo(NINE);
		assertThat(without.timings().getFirst().arrivalAt()).isNull();
	}

	@Test
	void 이동_시간을_모르면_다음_정한_도착_시각_전까지_null이다() {
		travel.willReturn(null);
		Instant noon = NINE.plusSeconds(3 * 3600);

		Result result = ScheduleCalculator.calculate(List.of(
			new PlannedItem(DAY, 1, 60, NINE, SPOT), item(2, 60), new PlannedItem(DAY, 3, 60, noon, SPOT),
			item(4, 60)), ScheduleOptions.EMPTY, Map.of(), TransportMode.TRANSIT, travel);

		assertThat(result.timings()).extracting(ScheduleCalculator.Timing::arrivalAt)
			.containsExactly(NINE, null, noon, null);
		assertThat(result.timings().get(1).legFromPrevious().unavailableReason()).isEqualTo("FAKE_UNAVAILABLE");
	}

	@Test
	void 일정_옵션은_JSON_맵으로_바꿨다_되돌려도_같다() {
		ScheduleOptions options = new ScheduleOptions("RELAXED",
			List.of(new DayOption(DAY, LocalTime.of(9, 30), LocalTime.of(18, 0), HOTEL, null,
				TravelLeg.found(15, new BigDecimal("1200.00"), "KAKAO_CAR", NINE))),
			List.of(new UnassignedPlace(401L, "END_TIME_EXCEEDED", DAY), new UnassignedPlace(402L, "NO_ROUTE", null)));

		assertThat(ScheduleOptions.fromMap(options.toMap())).isEqualTo(options);
		assertThat(ScheduleOptions.fromMap(Map.of())).isEqualTo(ScheduleOptions.EMPTY);
		assertThat(ScheduleOptions.EMPTY.toMap()).isEmpty();
	}

	@Test
	void 날짜_범위_밖_일차와_미배치_항목을_뺀다() {
		ScheduleOptions options = new ScheduleOptions(null,
			List.of(new DayOption(DAY, LocalTime.NOON, LocalTime.MIDNIGHT, null, null, null),
				new DayOption(DAY.plusDays(1), LocalTime.NOON, LocalTime.MIDNIGHT, null, null, null)),
			List.of(new UnassignedPlace(1L, "r", DAY.plusDays(1)), new UnassignedPlace(2L, "r", null)));

		ScheduleOptions within = options.within(DAY, DAY);

		assertThat(within.days()).extracting(DayOption::date).containsExactly(DAY);
		assertThat(within.unassignedPlaces()).extracting(UnassignedPlace::roomPlaceId).containsExactly(2L);
	}
}
