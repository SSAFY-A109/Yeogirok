package com.ssafy.yeogirok.domain.schedule.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleCalculator;
import com.ssafy.yeogirok.domain.schedule.service.ScheduleCalculator.PlannedItem;
import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.domain.schedule.travel.TravelTimeProvider;

/** 날짜·순서가 정해진 초안 항목의 도착·출발 시각과 구간을 확정 일정과 같은 규칙({@link ScheduleCalculator})으로 계산한다. */
public final class DraftTimings {

	private DraftTimings() {
	}

	/**
	 * @param items     배치·미배치 항목 전체. 배치 항목은 visitDate·order 가 있어야 한다
	 * @param locations itemId → 좌표
	 * @return 같은 순서의 항목(배치 항목은 시각·구간을 채우고, 미배치 항목은 비운다)과 마지막 날 도착지 구간
	 */
	public static Result calculate(List<DraftItem> items, List<DayOption> days, NamedPoint startPoint,
		NamedPoint endPoint, TransportMode mode, Map<Integer, GeoPoint> locations, TravelTimeProvider provider) {
		List<DraftItem> placed = items.stream().filter(DraftItem::placed).toList();
		List<PlannedItem> planned = placed.stream()
			.map(item -> new PlannedItem(item.visitDate(), item.order(), item.stayMinutes(),
				locations.get(item.itemId())))
			.toList();
		ScheduleOptions options = new ScheduleOptions(days, startPoint, endPoint, null, List.of());
		ScheduleCalculator.Result result = ScheduleCalculator.calculate(planned, options, days.getFirst().date(),
			days.getLast().date(), mode, provider);

		List<DraftItem> updated = new ArrayList<>();
		int index = 0;
		for (DraftItem item : items) {
			if (!item.placed()) {
				updated.add(DraftItem.unassigned(item.itemId(), item.roomPlaceId(), item.stayMinutes(), item.fixed()));
				continue;
			}
			ScheduleCalculator.Timing timing = result.timings().get(index++);
			updated.add(new DraftItem(item.itemId(), item.roomPlaceId(), item.visitDate(), item.order(),
				item.stayMinutes(), item.fixed(), timing.arrivalAt(), timing.departureAt(), timing.legFromPrevious()));
		}
		return new Result(updated, result.endLeg());
	}

	public record Result(List<DraftItem> items, TravelLeg endLeg) {
	}
}
