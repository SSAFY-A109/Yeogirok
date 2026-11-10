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

import com.ssafy.yeogirok.domain.room.entity.TransportMode;
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
 *
 * <p>일차의 구간·경고 규칙({@link #dayParts})은 일정 초안(API-038) 응답도 같이 쓴다.
 */
public final class ScheduleViews {

	private ScheduleViews() {
	}

	/** 확정 항목이 하나도 없으면 null(일정 없음, 명세). */
	public static ScheduleView of(TravelRoom room, List<ScheduleItem> items) {
		if (items.isEmpty()) {
			return null;
		}
		ScheduleOptions options = ScheduleOptions.fromMap(room.getScheduleOptions());

		Map<LocalDate, List<ScheduleItem>> itemsByDate = new TreeMap<>();
		options.days().forEach(day -> itemsByDate.put(day.date(), new ArrayList<>()));
		items.forEach(item -> itemsByDate.computeIfAbsent(item.getVisitDate(), date -> new ArrayList<>()).add(item));

		List<ScheduleView.Day> days = new ArrayList<>();
		itemsByDate.forEach((date, dayItems) -> {
			dayItems.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
			// 도착지 구간은 마지막 날에만 있다.
			TravelLeg endLeg = date.equals(room.getEndDate()) && !dayItems.isEmpty() ? options.endLeg() : null;
			DayOption option = options.day(date).orElse(null);
			List<Timed> timed = dayItems.stream()
				.map(item -> new Timed(item.getId(), item.getArrivalAt(), item.getDepartureAt(), item.legFromPrevious()))
				.toList();
			DayParts parts = dayParts(date, option, timed, endLeg, room.getTransportMode());
			List<ScheduleView.Item> viewItems = dayItems.stream()
				.map(item -> new ScheduleView.Item(item.getId(), item.getRoomPlaceId(), item.getPosition(),
					item.getArrivalAt(), item.getDepartureAt(), item.getStayMinutes(), item.isFixed()))
				.toList();
			days.add(new ScheduleView.Day(date,
				option == null ? null : option.startTime(),
				option == null ? null : option.endTime(),
				viewItems, parts.legs(), parts.warnings()));
		});
		List<ScheduleView.Unassigned> unassigned = options.unassignedPlaces().stream()
			.map(place -> new ScheduleView.Unassigned(place.roomPlaceId()))
			.toList();
		return new ScheduleView(days, unassigned, List.of(), point(options.startPoint()), point(options.endPoint()));
	}

	/**
	 * 일차의 시각이 정해진 항목 하나.
	 *
	 * @param id              확정 일정은 schedule_items.id, 초안은 itemId
	 * @param legFromPrevious 직전 지점이 없으면 null
	 */
	public record Timed(Long id, Instant arrivalAt, Instant departureAt, TravelLeg legFromPrevious) {
	}

	public record DayParts(List<ScheduleView.Leg> legs, List<String> warnings) {
	}

	/**
	 * 하루의 구간과 경고. 경고는 같은 일차에서 중복하지 않는다.
	 *
	 * @param items  방문 순서대로
	 * @param endLeg 마지막 항목 → 도착지 구간. 없으면 null
	 */
	public static DayParts dayParts(LocalDate date, DayOption option, List<Timed> items, TravelLeg endLeg,
		TransportMode mode) {
		List<ScheduleView.Leg> legs = new ArrayList<>();
		Set<String> warnings = new LinkedHashSet<>();
		Long previousId = null;
		for (Timed item : items) {
			if (item.legFromPrevious() != null) {
				legs.add(leg(mode, previousId, item.id(), item.legFromPrevious(), warnings));
			}
			if (item.arrivalAt() == null) {
				warnings.add(ScheduleView.WARNING_ARRIVAL_TIME_UNKNOWN);
			}
			previousId = item.id();
		}
		if (endLeg != null) {
			legs.add(leg(mode, previousId, null, endLeg, warnings));
		}
		if (exceedsEndTime(date, option, items, endLeg)) {
			warnings.add(ScheduleView.WARNING_END_TIME_EXCEEDED);
		}
		return new DayParts(legs, List.copyOf(warnings));
	}

	private static ScheduleView.Leg leg(TransportMode mode, Long fromId, Long toId, TravelLeg leg,
		Set<String> warnings) {
		if (!leg.isKnown()) {
			warnings.add(ScheduleView.WARNING_TRAVEL_TIME_UNAVAILABLE);
		}
		return new ScheduleView.Leg(fromId, toId, mode, leg.travelMinutes(), leg.source());
	}

	/** 마지막 출발 시각(도착지까지 이동 시간을 알면 더한다)이 일차 종료 시각보다 늦은지. 모르면 false. */
	private static boolean exceedsEndTime(LocalDate date, DayOption option, List<Timed> items, TravelLeg endLeg) {
		if (option == null || option.endTime() == null || items.isEmpty()) {
			return false;
		}
		Instant last = items.getLast().departureAt();
		if (last == null) {
			return false;
		}
		if (endLeg != null && endLeg.isKnown()) {
			last = last.plusSeconds(endLeg.travelMinutes() * 60L);
		}
		return last.isAfter(ZonedDateTime.of(date, option.endTime(), ApiDateTime.ZONE).toInstant());
	}

	public static ScheduleView.Point point(NamedPoint point) {
		return point == null ? null : new ScheduleView.Point(point.name(), point.latitude(), point.longitude());
	}
}
