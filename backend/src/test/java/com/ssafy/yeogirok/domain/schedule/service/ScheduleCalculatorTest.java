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
	static final LocalDate NEXT = DAY.plusDays(1);
	/** DAY 09:00 Asia/Seoul. */
	static final Instant NINE = Instant.parse("2026-11-05T00:00:00Z");
	/** NEXT 09:00 Asia/Seoul. */
	static final Instant NEXT_NINE = Instant.parse("2026-11-06T00:00:00Z");
	static final GeoPoint SPOT = new GeoPoint(new BigDecimal("37.75"), new BigDecimal("128.87"));
	static final NamedPoint STATION = new NamedPoint("강릉역", new BigDecimal("37.76"), new BigDecimal("128.89"));

	final FakeTravelTimeProvider travel = new FakeTravelTimeProvider(20);

	private static PlannedItem item(LocalDate date, int order, int stay) {
		return new PlannedItem(date, order, stay, SPOT);
	}

	private static DayOption nineToSix(LocalDate date) {
		return new DayOption(date, LocalTime.of(9, 0), LocalTime.of(18, 0));
	}

	private static ScheduleOptions options(NamedPoint start, NamedPoint end, DayOption... days) {
		return new ScheduleOptions(List.of(days), start, end, null, List.of());
	}

	private Result calculate(List<PlannedItem> items, ScheduleOptions options, LocalDate first, LocalDate last) {
		return ScheduleCalculator.calculate(items, options, first, last, TransportMode.CAR, travel);
	}

	@Test
	void 당일치기는_출발지에서_출발해_차례로_계산하고_도착지_구간도_구한다() {
		Result result = calculate(List.of(item(DAY, 2, 30), item(DAY, 1, 60)),
			options(STATION, STATION, nineToSix(DAY)), DAY, DAY);

		// order 1(입력 두 번째): 09:00 + 20분 이동 → 09:20 도착, 10:20 출발
		assertThat(result.timings().get(1).arrivalAt()).isEqualTo(NINE.plusSeconds(20 * 60));
		assertThat(result.timings().get(1).departureAt()).isEqualTo(NINE.plusSeconds(80 * 60));
		// order 2: 10:20 + 20분 → 10:40 도착, 11:10 출발
		assertThat(result.timings().get(0).arrivalAt()).isEqualTo(NINE.plusSeconds(100 * 60));
		assertThat(result.timings().get(0).departureAt()).isEqualTo(NINE.plusSeconds(130 * 60));
		assertThat(result.timings()).allSatisfy(timing -> assertThat(timing.legFromPrevious()).isNotNull());
		assertThat(result.endLeg().travelMinutes()).isEqualTo(20);
		assertThat(travel.calls()).hasSize(3);
	}

	@Test
	void 출발지는_첫날에만_도착지는_마지막_날에만_붙는다() {
		Result result = calculate(List.of(item(DAY, 1, 60), item(NEXT, 1, 60)),
			options(STATION, STATION, nineToSix(DAY), nineToSix(NEXT)), DAY, NEXT);

		// 첫날: 출발지 → 첫 장소 구간이 있고 09:20 도착
		assertThat(result.timings().get(0).legFromPrevious()).isNotNull();
		assertThat(result.timings().get(0).arrivalAt()).isEqualTo(NINE.plusSeconds(20 * 60));
		// 둘째 날: 출발 지점이 없어 이동 없이 09:00 도착
		assertThat(result.timings().get(1).legFromPrevious()).isNull();
		assertThat(result.timings().get(1).arrivalAt()).isEqualTo(NEXT_NINE);
		// 도착지 구간은 마지막 날 한 번
		assertThat(result.endLeg()).isNotNull();
		assertThat(travel.calls()).hasSize(2);
	}

	@Test
	void 마지막_날을_계산하지_않거나_도착지가_없으면_도착지_구간이_없다() {
		ScheduleOptions withEnd = options(null, STATION, nineToSix(DAY), nineToSix(NEXT));

		assertThat(calculate(List.of(item(DAY, 1, 60)), withEnd, DAY, NEXT).endLeg()).isNull();
		assertThat(calculate(List.of(item(NEXT, 1, 60)), options(null, null, nineToSix(NEXT)), DAY, NEXT).endLeg())
			.isNull();
	}

	@Test
	void 일차_시작_시각이_없으면_도착_시각을_만들지_않는다() {
		Result result = calculate(List.of(item(DAY, 1, 60), item(DAY, 2, 60)), ScheduleOptions.EMPTY, DAY, DAY);

		assertThat(result.timings()).extracting(ScheduleCalculator.Timing::arrivalAt).containsOnlyNulls();
		assertThat(result.timings()).extracting(ScheduleCalculator.Timing::departureAt).containsOnlyNulls();
	}

	@Test
	void 이동_시간을_모르면_그날_그_뒤_도착_시각은_모두_null이다() {
		travel.willReturn(null);

		Result result = calculate(List.of(item(DAY, 1, 60), item(DAY, 2, 60), item(DAY, 3, 60)),
			options(null, null, nineToSix(DAY)), DAY, DAY);

		assertThat(result.timings()).extracting(ScheduleCalculator.Timing::arrivalAt)
			.containsExactly(NINE, null, null);
		assertThat(result.timings().get(1).legFromPrevious().unavailableReason()).isEqualTo("FAKE_UNAVAILABLE");
	}

	@Test
	void 일정_옵션은_JSON_맵으로_바꿨다_되돌려도_같다() {
		ScheduleOptions options = new ScheduleOptions(
			List.of(new DayOption(DAY, LocalTime.of(9, 30), LocalTime.of(18, 0))), STATION,
			new NamedPoint(null, new BigDecimal("37.50"), new BigDecimal("127.00")),
			TravelLeg.found(15, new BigDecimal("1200.00"), "KAKAO_CAR", NINE),
			List.of(new UnassignedPlace(401L), new UnassignedPlace(402L)));

		assertThat(ScheduleOptions.fromMap(options.toMap())).isEqualTo(options);
		assertThat(ScheduleOptions.fromMap(Map.of())).isEqualTo(ScheduleOptions.EMPTY);
		assertThat(ScheduleOptions.EMPTY.toMap()).isEmpty();
	}

	@Test
	void 날짜_범위_밖_일차만_빼고_지점과_미배치_항목은_남긴다() {
		ScheduleOptions options = new ScheduleOptions(List.of(nineToSix(DAY), nineToSix(NEXT)), STATION, STATION,
			null, List.of(new UnassignedPlace(1L)));

		ScheduleOptions within = options.within(DAY, DAY);

		assertThat(within.days()).extracting(DayOption::date).containsExactly(DAY);
		assertThat(within.startPoint()).isEqualTo(STATION);
		assertThat(within.endPoint()).isEqualTo(STATION);
		assertThat(within.unassignedPlaces()).extracting(UnassignedPlace::roomPlaceId).containsExactly(1L);
	}
}
