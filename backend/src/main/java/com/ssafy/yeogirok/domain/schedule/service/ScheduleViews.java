package com.ssafy.yeogirok.domain.schedule.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.ssafy.yeogirok.domain.room.entity.TravelRoom;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.DayOption;
import com.ssafy.yeogirok.domain.schedule.ScheduleOptions.NamedPoint;
import com.ssafy.yeogirok.domain.schedule.dto.ScheduleView;
import com.ssafy.yeogirok.domain.schedule.entity.ScheduleItem;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;
import com.ssafy.yeogirok.global.response.ApiDateTime;

/**
 * 저장한 일정 항목과 일정 옵션으로 {@link ScheduleView} 를 만든다. 외부 호출 없이 저장한 값만 쓴다.
 * 후보를 뺐어도(room_places.removed_at) 그 후보를 가리키는 항목은 그대로 보여 준다.
 */
public final class ScheduleViews {

	private ScheduleViews() {
	}

	/** 항목·일차 조건·미배치 항목이 하나도 없으면 null(일정 없음). */
	public static ScheduleView of(TravelRoom room, List<ScheduleItem> items) {
		ScheduleOptions options = ScheduleOptions.fromMap(room.getScheduleOptions());
		if (items.isEmpty() && options.isEmpty()) {
			return null;
		}

		Map<LocalDate, List<ScheduleItem>> itemsByDate = new TreeMap<>();
		options.days().forEach(day -> itemsByDate.put(day.date(), new ArrayList<>()));
		items.forEach(item -> itemsByDate.computeIfAbsent(item.getVisitDate(), date -> new ArrayList<>()).add(item));

		List<ScheduleView.Day> days = new ArrayList<>();
		itemsByDate.forEach((date, dayItems) -> {
			dayItems.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
			days.add(day(room, date, options.day(date).orElse(null), dayItems));
		});
		List<ScheduleView.Unassigned> unassigned = options.unassignedPlaces().stream()
			.map(place -> new ScheduleView.Unassigned(place.roomPlaceId(), place.reason(), place.attemptedDate()))
			.toList();
		return new ScheduleView(room.getId(), days, unassigned, List.of());
	}

	private static ScheduleView.Day day(TravelRoom room, LocalDate date, DayOption option, List<ScheduleItem> items) {
		List<ScheduleView.Item> viewItems = new ArrayList<>();
		List<ScheduleView.Leg> legs = new ArrayList<>();
		Set<String> warnings = new LinkedHashSet<>();
		Long previousId = null;
		for (ScheduleItem item : items) {
			viewItems.add(new ScheduleView.Item(item.getId(), item.getRoomPlaceId(), item.getPosition(),
				item.getArrivalAt(), item.getDepartureAt(), item.getStayMinutes(), item.isFixed()));
			TravelLeg leg = item.legFromPrevious();
			if (leg != null) {
				legs.add(leg(room, previousId, item.getId(), leg, warnings));
			}
			if (item.getArrivalAt() == null) {
				warnings.add(ScheduleView.WARNING_ARRIVAL_TIME_UNKNOWN);
			}
			previousId = item.getId();
		}

		TravelLeg endLeg = option == null || items.isEmpty() ? null : option.endLeg();
		if (endLeg != null) {
			legs.add(leg(room, previousId, null, endLeg, warnings));
		}
		if (exceedsEndTime(date, option, items, endLeg)) {
			warnings.add(ScheduleView.WARNING_END_TIME_EXCEEDED);
		}
		return new ScheduleView.Day(date,
			option == null ? null : option.startTime(),
			option == null ? null : option.endTime(),
			point(option == null ? null : option.startPoint()),
			point(option == null ? null : option.endPoint()),
			viewItems, legs, List.copyOf(warnings));
	}

	private static ScheduleView.Leg leg(TravelRoom room, Long fromId, Long toId, TravelLeg leg, Set<String> warnings) {
		if (!leg.isKnown()) {
			warnings.add(ScheduleView.WARNING_TRAVEL_TIME_UNAVAILABLE);
		}
		return new ScheduleView.Leg(fromId, toId, room.getTransportMode(), leg.travelMinutes(), leg.source());
	}

	/** 마지막 출발 시각(도착 지점까지 이동 시간을 알면 더한다)이 일차 종료 시각보다 늦은지. 모르면 false. */
	private static boolean exceedsEndTime(LocalDate date, DayOption option, List<ScheduleItem> items,
		TravelLeg endLeg) {
		if (option == null || option.endTime() == null || items.isEmpty()) {
			return false;
		}
		Instant last = items.getLast().getDepartureAt();
		if (last == null) {
			return false;
		}
		if (endLeg != null && endLeg.isKnown()) {
			last = last.plusSeconds(endLeg.travelMinutes() * 60L);
		}
		return last.isAfter(ZonedDateTime.of(date, option.endTime(), ApiDateTime.ZONE).toInstant());
	}

	private static ScheduleView.Point point(NamedPoint point) {
		return point == null ? null : new ScheduleView.Point(point.name(), point.latitude(), point.longitude());
	}
}
