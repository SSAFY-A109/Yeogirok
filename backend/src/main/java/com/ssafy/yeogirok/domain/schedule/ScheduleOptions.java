package com.ssafy.yeogirok.domain.schedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 여행방 일정의 조건과 계산 결과 중 schedule_items 행에 담기지 않는 값. {@code travel_rooms.schedule_options}
 * (JSONB)에 저장한다.
 * <pre>
 * {"days": [{"date": "2026-10-23", "startTime": "09:00:00", "endTime": "18:00:00"}],
 *  "startPoint": {"name": "강릉역", "latitude": 37.76, "longitude": 128.89} | null,
 *  "endPoint": ... | null,
 *  "endLeg": {"travelMinutes": 20, "distanceMeters": 1500.00, "source": "KAKAO_CAR",
 *             "asOf": "2026-11-05T00:00:00Z", "unavailableReason": null} | null,
 *  "unassignedPlaces": [{"roomPlaceId": 401}]}
 * </pre>
 * 출발지는 첫날, 도착지는 마지막 날에만 쓴다(당일치기는 같은 날 둘 다). 숙소 등 중간 지점은 받지 않는다.
 * 첫날 출발지에서 첫 장소까지의 구간은 그 항목 행에 저장하고, 마지막 날 마지막 장소에서 도착지까지의 구간은 endLeg 다.
 * 일차 조건·지점은 일정 초안 저장(API-039)에서 채운다.
 *
 * <p>JSON 변환은 직접 한다. 엔티티의 {@code Map<String, Object>} 를 Jackson 설정에 기대지 않고 읽고 쓰기 위해서다.
 */
public record ScheduleOptions(List<DayOption> days, NamedPoint startPoint, NamedPoint endPoint, TravelLeg endLeg,
	List<UnassignedPlace> unassignedPlaces) {

	public static final ScheduleOptions EMPTY = new ScheduleOptions(List.of(), null, null, null, List.of());

	public ScheduleOptions {
		days = List.copyOf(days);
		unassignedPlaces = List.copyOf(unassignedPlaces);
	}

	public Optional<DayOption> day(LocalDate date) {
		return days.stream().filter(day -> day.date().equals(date)).findFirst();
	}

	/** 날짜 범위 안의 일차만 남긴다. 범위가 null 이면 일차를 모두 지운다. 지점·미배치 항목은 그대로다. */
	public ScheduleOptions within(LocalDate start, LocalDate end) {
		return new ScheduleOptions(days.stream().filter(day -> inRange(day.date(), start, end)).toList(),
			startPoint, endPoint, endLeg, unassignedPlaces);
	}

	/** 마지막 날 도착지 구간을 바꾼다. */
	public ScheduleOptions withEndLeg(TravelLeg leg) {
		return new ScheduleOptions(days, startPoint, endPoint, leg, unassignedPlaces);
	}

	private static boolean inRange(LocalDate date, LocalDate start, LocalDate end) {
		return start != null && !date.isBefore(start) && !date.isAfter(end);
	}

	/** 일차 조건. 시각은 Asia/Seoul 기준이다. */
	public record DayOption(LocalDate date, LocalTime startTime, LocalTime endTime) {
	}

	/** 출발·도착 지점. 이름은 없을 수 있다. 좌표는 끝자리 0 을 지운 값으로 맞춘다(JSON 왕복 뒤에도 같은 값). */
	public record NamedPoint(String name, BigDecimal latitude, BigDecimal longitude) {

		public NamedPoint {
			latitude = latitude == null ? null : latitude.stripTrailingZeros();
			longitude = longitude == null ? null : longitude.stripTrailingZeros();
		}

		public GeoPoint toGeoPoint() {
			return new GeoPoint(latitude, longitude);
		}
	}

	/** 일정에 넣지 못한 후보. 이유·추천 날짜는 두지 않는다(명세). */
	public record UnassignedPlace(Long roomPlaceId) {
	}

	/* ── JSON(Map) 변환 ───────────────────────────────────── */

	public static ScheduleOptions fromMap(Map<String, Object> map) {
		if (map == null || map.isEmpty()) {
			return EMPTY;
		}
		List<DayOption> days = new ArrayList<>();
		for (Map<String, Object> day : listOfMaps(map.get("days"))) {
			days.add(new DayOption(date(day.get("date")), time(day.get("startTime")), time(day.get("endTime"))));
		}
		List<UnassignedPlace> unassigned = new ArrayList<>();
		for (Map<String, Object> place : listOfMaps(map.get("unassignedPlaces"))) {
			unassigned.add(new UnassignedPlace(longValue(place.get("roomPlaceId"))));
		}
		return new ScheduleOptions(days, point(map.get("startPoint")), point(map.get("endPoint")),
			leg(map.get("endLeg")), unassigned);
	}

	/** 비어 있으면(EMPTY) 빈 맵이다. */
	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		if (equals(EMPTY)) {
			return map;
		}
		map.put("days", days.stream().map(day -> {
			Map<String, Object> value = new LinkedHashMap<>();
			value.put("date", day.date().toString());
			value.put("startTime", day.startTime() == null ? null : day.startTime().toString());
			value.put("endTime", day.endTime() == null ? null : day.endTime().toString());
			return value;
		}).toList());
		map.put("startPoint", pointMap(startPoint));
		map.put("endPoint", pointMap(endPoint));
		map.put("endLeg", legMap(endLeg));
		map.put("unassignedPlaces", unassignedPlaces.stream().map(place -> {
			Map<String, Object> value = new LinkedHashMap<>();
			value.put("roomPlaceId", jsonInteger(place.roomPlaceId()));
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
		value.put("latitude", jsonDecimal(point.latitude()));
		value.put("longitude", jsonDecimal(point.longitude()));
		return value;
	}

	private static Map<String, Object> legMap(TravelLeg leg) {
		if (leg == null) {
			return null;
		}
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("travelMinutes", leg.travelMinutes());
		value.put("distanceMeters", jsonDecimal(leg.distanceMeters()));
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
		BigDecimal distance = decimal(map.get("distanceMeters"));
		// 거리는 schedule_items.distance_from_previous_meters 와 같이 소수 둘째 자리까지다.
		return new TravelLeg(minutes == null ? null : ((Number)minutes).intValue(),
			distance == null ? null : distance.setScale(2, RoundingMode.HALF_UP),
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

	/*
	 * 맵에는 JSON 을 다시 읽었을 때와 같은 타입(정수는 Integer/Long, 소수는 Double)으로 넣는다. 타입이 다르면 Hibernate 가
	 * JSON 컬럼을 flush 할 때마다 바뀐 것으로 보고 version 을 계속 올리고, 저장한 맵과 읽은 맵의 equals 도 어긋난다.
	 */

	private static Number jsonInteger(Long value) {
		if (value == null) {
			return null;
		}
		return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? Integer.valueOf(value.intValue()) : value;
	}

	private static Double jsonDecimal(BigDecimal value) {
		return value == null ? null : value.doubleValue();
	}
}
