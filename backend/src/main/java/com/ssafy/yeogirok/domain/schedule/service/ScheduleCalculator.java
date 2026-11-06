package com.ssafy.yeogirok.domain.schedule.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.domain.schedule.travel.TravelTimeProvider;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * 날짜별 방문 순서대로 도착·출발 시각과 이동 구간을 계산한다. 이동 시간은 {@link TravelTimeProvider} 로 구하므로
 * 트랜잭션·방 잠금 밖에서 부른다.
 *
 * <p>규칙(근거 없는 시각을 만들지 않는다):
 * <ul>
 *   <li>하루의 기준 시각은 일차 시작 시각이다. 일차 시작 시각이 없으면 기준 시각을 모른다(기본값을 만들지 않는다).</li>
 *   <li>도착 시각은 사용자가 정하지 않고 항상 계산한다. 도착 시각 = 직전 출발(또는 기준) 시각 + 이동 시간.
 *   첫 항목인데 출발 지점이 없으면 이동 없이 기준 시각에 도착한다. 출발 지점은 첫날 출발지뿐이다.
 *   사용자는 앞 장소의 체류 시간을 바꿔 뒤 시각을 조정한다.</li>
 *   <li>이동 시간이나 직전 시각을 모르면 도착 시각을 null 로 두고, 그날 그 뒤 항목도 null 이다.</li>
 *   <li>출발 시각 = 도착 시각 + 체류 시간.</li>
 * </ul>
 */
public final class ScheduleCalculator {

	private ScheduleCalculator() {
	}

	/**
	 * @param items          계산할 날짜의 항목 전체. 날짜 안에서는 order 순서대로 놓는다
	 * @param firstDay       여행 첫날. 이날만 출발지(options.startPoint)에서 출발한다
	 * @param lastDay        여행 마지막 날. 이날만 도착지(options.endPoint)로 간다
	 * @return 항목 순서와 같은 순서의 시각·구간과 마지막 날 도착지 구간(계산했을 때만)
	 */
	public static Result calculate(List<PlannedItem> items, ScheduleOptions options, LocalDate firstDay,
		LocalDate lastDay, TransportMode mode, TravelTimeProvider travelTimeProvider) {
		Map<LocalDate, List<Integer>> indexesByDate = new TreeMap<>();
		for (int i = 0; i < items.size(); i++) {
			indexesByDate.computeIfAbsent(items.get(i).visitDate(), date -> new ArrayList<>()).add(i);
		}

		Timing[] timings = new Timing[items.size()];
		TravelLeg[] endLeg = new TravelLeg[1];
		indexesByDate.forEach((date, indexes) -> {
			indexes.sort((a, b) -> Integer.compare(items.get(a).order(), items.get(b).order()));
			DayOption day = options.day(date).orElse(null);
			Instant cursor = day == null || day.startTime() == null ? null
				: ZonedDateTime.of(date, day.startTime(), ApiDateTime.ZONE).toInstant();
			GeoPoint previous = date.equals(firstDay) && options.startPoint() != null
				? options.startPoint().toGeoPoint() : null;

			for (int index : indexes) {
				PlannedItem item = items.get(index);
				TravelLeg leg = previous == null ? null : travelTimeProvider.estimate(previous, item.location(), mode);
				Instant arrival;
				if (cursor == null) {
					arrival = null;
				} else if (leg == null) {
					arrival = cursor;
				} else {
					arrival = leg.isKnown() ? cursor.plusSeconds(leg.travelMinutes() * 60L) : null;
				}
				Instant departure = arrival == null ? null : arrival.plusSeconds(item.stayMinutes() * 60L);
				timings[index] = new Timing(arrival, departure, leg);
				cursor = departure;
				previous = item.location();
			}

			if (date.equals(lastDay) && options.endPoint() != null && previous != null) {
				endLeg[0] = travelTimeProvider.estimate(previous, options.endPoint().toGeoPoint(), mode);
			}
		});
		return new Result(List.of(timings), endLeg[0]);
	}

	/** 계산할 항목. */
	public record PlannedItem(LocalDate visitDate, int order, int stayMinutes, GeoPoint location) {
	}

	/** @param legFromPrevious 직전 지점이 없으면 null */
	public record Timing(Instant arrivalAt, Instant departureAt, TravelLeg legFromPrevious) {
	}

	/**
	 * @param timings 입력 항목과 같은 순서
	 * @param endLeg  마지막 날 마지막 항목 → 도착지 구간. 마지막 날에 항목이 있고 도착지가 있을 때만, 아니면 null
	 */
	public record Result(List<Timing> timings, TravelLeg endLeg) {
	}
}
