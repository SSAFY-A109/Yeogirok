import type { RoomPlaceDto } from "../../../features/room/model";
import type {
  CreateScheduleDraftRequestDto,
  ScheduleBodyDto,
  ScheduleDayDto,
  ScheduleDraftStopRequestDto,
  ScheduleStopDto,
  SchedulePaceDto,
  UnplacedStopDto,
} from "../../../features/schedule/model";

import { findCatalogPlace } from "./place";

// 목 서버의 일정 계산기. 실제 서버의 AI·길찾기를 흉내 낼 뿐이며, 값은 화면 확인용 더미다.
// 이동 시간은 두 지점의 직선 거리로 대충 잡는다. 좌표는 공개 장소의 대략적인 값이다.

type Point = { latitude: number; longitude: number };
type Endpoint = Point & { name: string };

/** 여행지별 출발·도착 지점. 주소 앞부분으로 고른다. 없으면 첫 장소를 출발 지점으로 쓴다. */
const STARTING_POINTS: readonly { addressPrefix: string; endpoint: Endpoint }[] = [
  { addressPrefix: "강릉시", endpoint: { name: "강릉역", latitude: 37.7642, longitude: 128.8996 } },
  { addressPrefix: "부산", endpoint: { name: "부산역", latitude: 35.1151, longitude: 129.0422 } },
  { addressPrefix: "속초시", endpoint: { name: "속초고속버스터미널", latitude: 38.2047, longitude: 128.5913 } },
  { addressPrefix: "제주시", endpoint: { name: "제주국제공항", latitude: 33.5066, longitude: 126.4929 } },
  { addressPrefix: "서귀포시", endpoint: { name: "제주국제공항", latitude: 33.5066, longitude: 126.4929 } },
];

const LODGING_NAME = "숙소";

/** 카테고리 기본 머무는 시간(분). */
const DEFAULT_STAY_MINUTES: Readonly<Record<string, number>> = {
  NATURE: 50,
  CULTURE: 60,
  SIGHT: 40,
  FOOD: 60,
  CAFE: 60,
  ACTIVITY: 90,
  SHOPPING: 60,
  EVENT: 90,
};

const FALLBACK_STAY_MINUTES = 60;
const PACE_STAY_RATIO: Readonly<Record<SchedulePaceDto, number>> = { RELAXED: 1.25, NORMAL: 1, PACKED: 0.75 };

const EARTH_RADIUS_KM = 6371;
const DETOUR_RATIO = 1.4;
const CAR_KMH = 35;
const TRANSIT_KMH = 18;
const TRANSIT_WAIT_MINUTES = 8;
const ROUND_MINUTES = 5;

const MEAL_TAGS: readonly { tag: string; from: number; to: number }[] = [
  { tag: "점심", from: 11 * 60, to: 14 * 60 },
  { tag: "저녁", from: 17 * 60, to: 20 * 60 },
];

type PlannedPlace = { roomPlaceId: number; name: string; category: RoomPlaceDto["category"]; point: Point; stayMinutes: number; defaultStayMinutes: number };

function toMinutes(time: string): number {
  const [hours, minutes] = time.split(":").map(Number);
  return hours * 60 + minutes;
}

function toTime(minutes: number): string {
  const clamped = Math.min(Math.max(minutes, 0), 23 * 60 + 59);
  return `${String(Math.floor(clamped / 60)).padStart(2, "0")}:${String(clamped % 60).padStart(2, "0")}`;
}

function distanceKm(a: Point, b: Point): number {
  const toRadians = (degrees: number) => {
    return (degrees * Math.PI) / 180;
  };

  const dLat = toRadians(b.latitude - a.latitude);
  const dLng = toRadians(b.longitude - a.longitude);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRadians(a.latitude)) * Math.cos(toRadians(b.latitude)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
}

function travelMinutes(from: Point, to: Point, transportMode: string): number {
  const km = distanceKm(from, to) * DETOUR_RATIO;
  const raw = transportMode === "CAR" ? (km / CAR_KMH) * 60 : (km / TRANSIT_KMH) * 60 + TRANSIT_WAIT_MINUTES;
  return Math.max(ROUND_MINUTES, Math.ceil(raw / ROUND_MINUTES) * ROUND_MINUTES);
}

function defaultStayOf(category: RoomPlaceDto["category"]): number {
  return category === null ? FALLBACK_STAY_MINUTES : (DEFAULT_STAY_MINUTES[category.code] ?? FALLBACK_STAY_MINUTES);
}

function startingPointOf(places: readonly PlannedPlace[], roomPlaces: readonly RoomPlaceDto[]): Endpoint {
  const firstAddress = roomPlaces.map((place) => {
    return findCatalogPlace(place.placeId)?.address ?? "";
  }).find((address) => {
    return address !== "";
  });

  const known = STARTING_POINTS.find((item) => {
    return firstAddress?.startsWith(item.addressPrefix) ?? false;
  });

  if (known !== undefined) {
    return known.endpoint;
  }

  const first = places[0];
  return { name: "출발 지점", latitude: first?.point.latitude ?? 37.5663, longitude: first?.point.longitude ?? 126.9779 };
}

function toPlannedPlace(roomPlace: RoomPlaceDto, stayMinutes: number | null, pace: SchedulePaceDto): PlannedPlace | null {
  const catalog = findCatalogPlace(roomPlace.placeId);

  if (catalog === null) {
    return null;
  }

  const defaultStayMinutes = defaultStayOf(roomPlace.category);
  const paced = Math.round((defaultStayMinutes * PACE_STAY_RATIO[pace]) / 10) * 10;

  return {
    roomPlaceId: roomPlace.roomPlaceId,
    name: roomPlace.name,
    category: roomPlace.category,
    point: { latitude: catalog.latitude, longitude: catalog.longitude },
    stayMinutes: stayMinutes ?? paced,
    defaultStayMinutes,
  };
}

function mealTagOf(place: PlannedPlace, arrival: number): string | null {
  if (place.category?.code !== "FOOD") {
    return null;
  }

  return MEAL_TAGS.find((meal) => {
    return arrival >= meal.from && arrival < meal.to;
  })?.tag ?? null;
}

type DayFrame = { date: string; startTime: string; endTime: string; origin: Endpoint; destination: Endpoint };

/** 주어진 순서로 하루를 짠다. 종료 시각까지 도착 지점에 돌아올 수 없는 장소는 넣지 못한 장소로 뺀다. */
function planDay(frame: DayFrame, ordered: readonly PlannedPlace[], transportMode: string): { day: ScheduleDayDto; unplaced: PlannedPlace[] } {
  const end = toMinutes(frame.endTime);
  const stops: ScheduleStopDto[] = [];
  const unplaced: { place: PlannedPlace; dto: UnplacedStopDto }[] = [];
  let clock = toMinutes(frame.startTime);
  let previous: Point = frame.origin;

  for (const place of ordered) {
    const travel = travelMinutes(previous, place.point, transportMode);
    const arrival = clock + travel;
    const departure = arrival + place.stayMinutes;
    const back = travelMinutes(place.point, frame.destination, transportMode);

    if (departure + back > end) {
      unplaced.push({
        place,

        dto: {
          roomPlaceId: place.roomPlaceId,
          name: place.name,
          category: place.category,
          stayMinutes: place.stayMinutes,
          reason: `${frame.endTime}까지 ${frame.destination.name}에 돌아올 수 없어요 (편도 ${back}분)`,
          suggestedDate: null,
        },
      });

      continue;
    }

    stops.push({
      roomPlaceId: place.roomPlaceId,
      name: place.name,
      category: place.category,
      tag: mealTagOf(place, arrival),
      arrivalTime: toTime(arrival),
      departureTime: toTime(departure),
      stayMinutes: place.stayMinutes,
      defaultStayMinutes: place.defaultStayMinutes,
      travelMinutes: travel,
      parking: transportMode !== "CAR" || place.category?.code === "CAFE" ? null : "AVAILABLE",
    });

    clock = departure;
    previous = place.point;
  }

  const returnTravel = travelMinutes(previous, frame.destination, transportMode);

  const totalTravel = stops.reduce((sum, stop) => {
    return sum + (stop.travelMinutes ?? 0);
  }, returnTravel);

  const totalStay = stops.reduce((sum, stop) => {
    return sum + stop.stayMinutes;
  }, 0);

  return {
    day: {
      date: frame.date,
      startTime: frame.startTime,
      endTime: frame.endTime,
      origin: { name: frame.origin.name, time: frame.startTime },
      destination: { name: frame.destination.name, time: toTime(clock + returnTravel), travelMinutes: returnTravel },
      totalTravelMinutes: totalTravel,
      totalStayMinutes: totalStay,
      stops,

      unplaced: unplaced.map((item) => {
        return item.dto;
      }),
    },

    unplaced: unplaced.map((item) => {
      return item.place;
    }),
  };
}

/** 가까운 곳부터 차례로 고른다(목의 "AI" 순서). */
function orderByNearest(start: Point, places: readonly PlannedPlace[]): PlannedPlace[] {
  const rest = [...places];
  const ordered: PlannedPlace[] = [];
  let current = start;

  while (rest.length > 0) {
    let nearestIndex = 0;

    rest.forEach((place, index) => {
      if (distanceKm(current, place.point) < distanceKm(current, rest[nearestIndex].point)) {
        nearestIndex = index;
      }
    });

    const [next] = rest.splice(nearestIndex, 1);
    ordered.push(next);
    current = next.point;
  }

  return ordered;
}

/** 넣지 못한 장소에 권할 다른 날(장소가 가장 적은 날). 하루짜리 여행이면 `null`. */
function withSuggestedDates(days: ScheduleDayDto[]): ScheduleDayDto[] {
  return days.map((day) => {
    const others = days.filter((other) => {
      return other.date !== day.date;
    });

    const lightest = [...others].sort((a, b) => {
      return a.stops.length - b.stops.length;
    })[0];

    return {
      ...day,

      unplaced: day.unplaced.map((stop) => {
        return { ...stop, suggestedDate: lightest?.date ?? null };
      }),
    };
  });
}

type PlanInput = { roomPlaces: readonly RoomPlaceDto[]; transportMode: string; request: CreateScheduleDraftRequestDto };

/**
 * 초안을 짠다. 날마다 `stops`가 있으면 그 순서·머무는 시간으로 시각만 계산하고("다시 계산"),
 * 모든 날이 `null`이면 담은 장소 전부를 가까운 순서로 앞날부터 채운다(W13 "일정 초안 만들기").
 */
export function planSchedule({ roomPlaces, transportMode, request }: PlanInput): ScheduleBodyDto {
  const byId = new Map(roomPlaces.map((place) => {
    return [place.roomPlaceId, place];
  }));

  const allPlaces = roomPlaces.flatMap((place) => {
    const planned = toPlannedPlace(place, null, request.pace);
    return planned === null ? [] : [planned];
  });

  const station = startingPointOf(allPlaces, roomPlaces);
  const lodging: Endpoint = { ...station, name: LODGING_NAME };
  const lastIndex = request.days.length - 1;

  const frames: DayFrame[] = request.days.map((day, index) => {
    return {
      date: day.date,
      startTime: day.startTime,
      endTime: day.endTime,
      origin: index === 0 ? station : lodging,
      destination: index === lastIndex ? station : lodging,
    };
  });

  const explicit = request.days.some((day) => {
    return day.stops !== null;
  });

  if (explicit) {
    const days = request.days.map((day, index) => {
      const ordered = (day.stops ?? []).flatMap((stop: ScheduleDraftStopRequestDto) => {
        const roomPlace = byId.get(stop.roomPlaceId);
        const planned = roomPlace === undefined ? null : toPlannedPlace(roomPlace, stop.stayMinutes, request.pace);
        return planned === null ? [] : [planned];
      });

      return planDay(frames[index], ordered, transportMode).day;
    });

    return { transportMode, pace: request.pace, days: withSuggestedDates(days) };
  }

  let remaining = orderByNearest(station, allPlaces);

  const days = frames.map((frame, index) => {
    const isLast = index === lastIndex;
    const { day, unplaced } = planDay(frame, remaining, transportMode);
    remaining = unplaced;

    // 마지막 날이 아니면 남은 장소는 다음 날로 넘기고, 그날의 "넣지 못한 장소"로는 보이지 않는다.
    return isLast ? day : { ...day, unplaced: [] };
  });

  return { transportMode, pace: request.pace, days: withSuggestedDates(days) };
}
