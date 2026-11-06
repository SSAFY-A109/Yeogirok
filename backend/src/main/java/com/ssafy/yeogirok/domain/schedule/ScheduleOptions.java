package com.ssafy.yeogirok.domain.schedule;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ssafy.yeogirok.domain.schedule.travel.GeoPoint;
import com.ssafy.yeogirok.domain.schedule.travel.TravelLeg;

/**
 * 여행방 일정의 일차 조건과 계산 결과 중 schedule_items 행에 담기지 않는 값. {@code travel_rooms.schedule_options}
 * (JSONB)에 저장한다.
 * <pre>
 * {"pace": "NORMAL",
 *  "days": [{"date": "2026-10-23", "startTime": "09:00:00", "endTime": "18:00:00",
 *            "startPoint": {"name": "숙소", "latitude": 37.5, "longitude": 127.0} | null,
 *            "endPoint": ... | null,
 *            "endLeg": {"travelMinutes": 20, "distanceMeters": 1500.00, "source": "KAKAO_CAR",
 *                       "asOf": "2026-11-05T00:00:00Z", "unavailableReason": null} | null}],
 *  "unassignedPlaces": [{"roomPlaceId": 401, "reason": "...", "attemptedDate": "2026-10-23" | null}]}
 * </pre>
 * 일차 조건은 일정 초안 저장(API-039)에서 채운다. endLeg 는 마지막 항목에서 도착 지점까지의 구간이다.
 *
 * <p>JSON 변환은 직접 한다. 엔티티의 {@code Map<String, Object>} 를 Jackson 설정에 기대지 않고 읽고 쓰기 위해서다.
 */
public record ScheduleOptions(String pace, List<DayOption> days, List<UnassignedPlace> unassignedPlaces) {

	public static final ScheduleOptions EMPTY = new ScheduleOptions(null, List.of(), List.of());

	public ScheduleOptions {
		days = List.copyOf(days);
		unassignedPlaces = List.copyOf(unassignedPlaces);
	}

	public boolean isEmpty() {
		return days.isEmpty() && unassignedPlaces.isEmpty();
	}

	public Optional<DayOption> day(LocalDate date) {
		return days.stream().filter(day -> day.date().equals(date)).findFirst();
	}

	/** 날짜 범위 안의 일차·미배치 항목만 남긴다. 범위가 null 이면 모두 지운다. */
	public ScheduleOptions within(LocalDate start, LocalDate end) {
		return new ScheduleOptions(pace,
			days.stream().filter(day -> inRange(day.date(), start, end)).toList(),
			unassignedPlaces.stream()
				.filter(place -> place.attemptedDate() == null || inRange(place.attemptedDate(), start, end))
				.toList());
	}

	/** 일차의 도착 지점 구간을 바꾼다. 일차 조건이 없는 날은 바꿀 것이 없다. */
	public ScheduleOptions withEndLegs(Map<LocalDate, TravelLeg> endLegs) {
		return new ScheduleOptions(pace, days.stream()
			.map(day -> endLegs.containsKey(day.date()) ? day.withEndLeg(endLegs.get(day.date())) : day)
			.toList(), unassignedPlaces);
	}

	private static boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
		return start != null && !date.isBefore(start) && !date.isAfter(end);
	}

	/** 일차 조건. 시각은 Asia/Seoul 기준이다. */
	public record DayOption(LocalDate date, LocalTime startTime, LocalTime endTime, NamedPoint startPoint,
		NamedPoint endPoint, TravelLeg endLeg) {

		DayOption withEndLeg(TravelLeg leg) {
			return new DayOption(date, startTime, endTime, startPoint, endPoint, leg);
		}
	}

	/** 출발·도착 지점. 이름은 없을 수 있다. */
	public record NamedPoint(String name, BigDecimal latitude, BigDecimal longitude) {

		public GeoPoint toGeoPoint() {
			return new GeoPoint(latitude, longitude);
		}
	}

	/** 일정에 넣지 못한 후보. */
	public record UnassignedPlace(Long roomPlaceId, String reason, LocalDate attemptedDate) {
	}

	/* ── JSON(Map) 변환 ───────────────────────────────────── */

	public static ScheduleOptions fromMap(Map<String, Object> map) {
		if (map == null || map.isEmpty()) {
			return EMPTY;
		}
		List<DayOption> days = new ArrayList<>();
		for (Map<String, Object> day : listOfMaps(map.get("days"))) {
			days.add(new DayOption(date(day.get("date")), time(day.get("startTime")), time(day.get("endTime")),
				point(day.get("startPoint")), point(day.get("endPoint")), leg(day.get("endLeg"))));
		}
		List<UnassignedPlace> unassigned = new ArrayList<>();
		for (Map<String, Object> place : listOfMaps(map.get("unassignedPlaces"))) {
			unassigned.add(new UnassignedPlace(longValue(place.get("roomPlaceId")), (String)place.get("reason"),
				date(place.get("attemptedDate"))));
		}
		return new ScheduleOptions((String)map.get("pace"), days, unassigned);
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		if (isEmpty() && pace == null) {
			return map;
		}
		map.put("pace", pace);
		map.put("days", days.stream().map(day -> {
			Map<String, Object> value = new LinkedHashMap<>();
			value.put("date", day.date().toString());
			value.put("startTime", day.startTime() == null ? null : day.startTime().toString());
			value.put("endTime", day.endTime() == null ? null : day.endTime().toString());
			value.put("startPoint", pointMap(day.startPoint()));
			value.put("endPoint", pointMap(day.endPoint()));
			value.put("endLeg", legMap(day.endLeg()));
			return value;
		}).toList());
		map.put("unassignedPlaces", unassignedPlaces.stream().map(place -> {
			Map<String, Object> value = new LinkedHashMap<>();
			value.put("roomPlaceId", place.roomPlaceId());
			value.put("reason", place.reason());
			value.put("attemptedDate", place.attemptedDate() == null ? null : place.attemptedDate().toString());
			return value;
		}).toList());
		return map;
	}

	private static Map<String, Object> pointMap(NamedPoint point) {
		if (point == null) {
			return null;
		}
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("name", point.name());
		value.put("latitude", point.latitude());
		value.put("longitude", point.longitude());
		return value;
	}

	private static Map<String, Object> legMap(TravelLeg leg) {
		if (leg == null) {
			return null;
		}
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("travelMinutes", leg.travelMinutes());
		value.put("distanceMeters", leg.distanceMeters());
		value.put("source", leg.source());
		value.put("asOf", leg.asOf() == null ? null : leg.asOf().toString());
		value.put("unavailableReason", leg.unavailableReason());
		return value;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> listOfMaps(Object value) {
		return value instanceof List<?> list ? (List<Map<String, Object>>)list : List.of();
	}

	@SuppressWarnings("unchecked")
	private static NamedPoint point(Object value) {
		if (!(value instanceof Map<?, ?> raw)) {
			return null;
		}
		Map<String, Object> map = (Map<String, Object>)raw;
		return new NamedPoint((String)map.get("name"), decimal(map.get("latitude")), decimal(map.get("longitude")));
	}

	@SuppressWarnings("unchecked")
	private static TravelLeg leg(Object value) {
		if (!(value instanceof Map<?, ?> raw)) {
			return null;
		}
		Map<String, Object> map = (Map<String, Object>)raw;
		Object minutes = map.get("travelMinutes");
		Object asOf = map.get("asOf");
		return new TravelLeg(minutes == null ? null : ((Number)minutes).intValue(), decimal(map.get("distanceMeters")),
			(String)map.get("source"), asOf == null ? null : Instant.parse((String)asOf),
			(String)map.get("unavailableReason"));
	}

	private static LocalDate date(Object value) {
		return value == null ? null : LocalDate.parse((String)value);
	}

	private static LocalTime time(Object value) {
		return value == null ? null : LocalTime.parse((String)value);
	}

	private static Long longValue(Object value) {
		return value == null ? null : ((Number)value).longValue();
	}

	private static BigDecimal decimal(Object value) {
		return value == null ? null : new BigDecimal(value.toString());
	}
}
