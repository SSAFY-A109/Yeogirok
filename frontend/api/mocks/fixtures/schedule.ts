import type { RoomPlaceDto, TransportModeDto } from "../../../features/room/model";
import type {
  DayConditionDto,
  DraftScheduleDto,
  ScheduleDraftDto,
  ScheduleItemDto,
  ScheduleLegDto,
  ScheduleViewDto,
} from "../../../features/schedule/model";
import { formatServerDateTime } from "../../../lib/date";

// 목 서버의 일정 계산기. 실제 서버의 배치·길찾기를 흉내 낼 뿐이며, 값은 화면 확인용 더미다.
// 이동 시간은 두 지점의 직선 거리로 대충 잡는다. 좌표는 공개 장소의 대략적인 값이다.
// 출발·도착 지점은 받지 않는다(늘 `null`). 그날 첫 장소에는 시작 시각에 도착하고 마지막 장소 뒤에는 구간이 없다.

type Point = { latitude: number; longitude: number };

/** 새로 배치하는 항목의 기본 머무는 시간(분, 서버 `DEFAULT_STAY_MINUTES`). */
export const MOCK_DEFAULT_STAY_MINUTES = 60;

const EARTH_RADIUS_KM = 6371;
const DETOUR_RATIO = 1.4;
const CAR_KMH = 35;
const TRANSIT_KMH = 18;
const TRANSIT_WAIT_MINUTES = 8;
const ROUND_MINUTES = 5;
const MINUTES_PER_HOUR = 60;
const MS_PER_MINUTE = 60_000;

const WARNING_TRAVEL_TIME_UNAVAILABLE = "TRAVEL_TIME_UNAVAILABLE";
const WARNING_ARRIVAL_TIME_UNKNOWN = "ARRIVAL_TIME_UNKNOWN";
const WARNING_END_TIME_EXCEEDED = "END_TIME_EXCEEDED";

/** `HH:mm:ss`를 하루 안의 분으로. */
function toMinutes(time: string): number {
  const [hours, minutes] = time.split(":").map(Number);
  return hours * MINUTES_PER_HOUR + minutes;
}

/** 그날 자정(Asia/Seoul)에서 `minutes`분 지난 시각을 계약의 datetime으로. */
function toDateTime(date: string, minutes: number): string {
  return formatServerDateTime(new Date(Date.parse(`${date}T00:00:00+09:00`) + minutes * MS_PER_MINUTE));
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

function travelMinutes(from: Point, to: Point, transportMode: TransportModeDto): number {
  const km = distanceKm(from, to) * DETOUR_RATIO;
  const raw = transportMode === "CAR" ? (km / CAR_KMH) * MINUTES_PER_HOUR : (km / TRANSIT_KMH) * MINUTES_PER_HOUR + TRANSIT_WAIT_MINUTES;
  return Math.max(ROUND_MINUTES, Math.ceil(raw / ROUND_MINUTES) * ROUND_MINUTES);
}

function pointOf(places: readonly RoomPlaceDto[], roomPlaceId: number): Point | null {
  return places.find((place) => {
    return place.id === roomPlaceId;
  })?.place ?? null;
}

// #region 하루 계산

/** 계산할 항목. `id`는 확정 일정이면 항목 id, 초안이면 itemId다. */
export type MockPlannedItem = { id: number; roomPlaceId: number; stayMinutes: number; fixed: boolean };

export type MockPlannedDay = DayConditionDto & { items: MockPlannedItem[] };

export type MockCalculatedDay = DayConditionDto & {
  items: ScheduleItemDto[];
  legs: ScheduleLegDto[];
  warnings: string[];
  /** 알려진 시각으로 마지막 일정이 그날 종료 시각을 넘는지. */
  exceedsEndTime: boolean;
};

/**
 * 주어진 순서로 하루의 도착·출발 시각과 이동 구간을 계산한다(서버 `ScheduleCalculator`).
 * 좌표를 모르는 장소가 끼면 그 구간의 이동 시간과 뒤 항목의 시각은 `null`이다.
 */
export function calculateMockDay(day: MockPlannedDay, places: readonly RoomPlaceDto[], transportMode: TransportModeDto): MockCalculatedDay {
  const source = transportMode === "CAR" ? "KAKAO_CAR" : "KAKAO_TRANSIT";
  const items: ScheduleItemDto[] = [];
  const legs: ScheduleLegDto[] = [];
  const warnings = new Set<string>();
  let clock: number | null = toMinutes(day.startTime);
  let previous: { id: number; point: Point | null } | null = null;

  for (const [index, item] of day.items.entries()) {
    const point = pointOf(places, item.roomPlaceId);

    if (previous !== null) {
      const travel = previous.point === null || point === null ? null : travelMinutes(previous.point, point, transportMode);
      legs.push({ fromItemId: previous.id, toItemId: item.id, transportMode, travelMinutes: travel, source });
      clock = clock === null || travel === null ? null : clock + travel;

      if (travel === null) {
        warnings.add(WARNING_TRAVEL_TIME_UNAVAILABLE);
      }
    }

    if (clock === null) {
      warnings.add(WARNING_ARRIVAL_TIME_UNKNOWN);
    }

    const departure: number | null = clock === null ? null : clock + item.stayMinutes;

    items.push({
      id: item.id,
      roomPlaceId: item.roomPlaceId,
      order: index + 1,
      arrivalAt: clock === null ? null : toDateTime(day.date, clock),
      departureAt: departure === null ? null : toDateTime(day.date, departure),
      stayMinutes: item.stayMinutes,
      fixed: item.fixed,
    });

    clock = departure;
    previous = { id: item.id, point };
  }

  const exceedsEndTime = day.items.length > 0 && clock !== null && clock > toMinutes(day.endTime);

  if (exceedsEndTime) {
    warnings.add(WARNING_END_TIME_EXCEEDED);
  }

  return { date: day.date, startTime: day.startTime, endTime: day.endTime, items, legs, warnings: [...warnings], exceedsEndTime };
}

function toViewDay({ date, startTime, endTime, items, legs, warnings }: MockCalculatedDay) {
  return { date, startTime, endTime, items, legs, warnings };
}

/** 계산한 날들을 확정 일정 응답(API-036 `schedule`) 모양으로. */
export function toMockScheduleView(days: readonly MockCalculatedDay[], unassignedRoomPlaceIds: readonly number[]): ScheduleViewDto {
  return {
    days: days.map(toViewDay),

    unassignedPlaces: unassignedRoomPlaceIds.map((roomPlaceId) => {
      return { roomPlaceId };
    }),

    warnings: [],
    startPoint: null,
    endPoint: null,
  };
}

// #endregion

// #region 확정 일정 fixture

/** fixture 일정 항목 id. 목 DB의 일련번호와 겹치지 않게 따로 센다. */
let nextFixtureItemId = 7001;

type FixtureScheduleInput = {
  places: RoomPlaceDto[];
  transportMode: TransportModeDto;
  /** 시각은 `HH:mm:ss`. */
  days: { date: string; startTime: string; endTime: string; roomPlaceIds: number[] }[];
};

/** 여행방 fixture의 확정 일정. 날마다 준 순서 그대로, 머무는 시간은 기본값으로 계산한다. */
export function planFixtureSchedule({ places, transportMode, days }: FixtureScheduleInput): ScheduleViewDto {
  const calculated = days.map(({ roomPlaceIds, ...condition }) => {
    const items = roomPlaceIds.map((roomPlaceId) => {
      const id = nextFixtureItemId;
      nextFixtureItemId += 1;
      return { id, roomPlaceId, stayMinutes: MOCK_DEFAULT_STAY_MINUTES, fixed: false };
    });

    return calculateMockDay({ ...condition, items }, places, transportMode);
  });

  return toMockScheduleView(calculated, []);
}

// #endregion

// #region 초안

/** 초안 항목. 일정에 넣지 않았으면 `visitDate`와 `order`가 `null`이다. */
export type MockDraftItem = {
  itemId: number;
  roomPlaceId: number;
  visitDate: string | null;
  order: number | null;
  stayMinutes: number;
  fixed: boolean;
};

export type MockJobError = { code: string; message: string };

/** 목 서버가 들고 있는 일정 초안(서버 `ScheduleDraft`). */
export type MockScheduleDraft = {
  roomId: number;
  draftKey: string;
  /** 마지막으로 이 초안을 계산한(하는) 작업. */
  jobId: string;
  /** API-037 멱등키와 요청 본문. 같은 키·같은 내용이 다시 오면 같은 초안을 돌려준다. */
  idempotencyKey: string;
  requestBody: string;
  /** 계산의 기준이 된 여행방 version. */
  baseVersion: number;
  /** 계산을 마칠 때마다 1씩 오른다. 처음 계산 전에는 0. */
  draftVersion: number;
  transportMode: TransportModeDto;
  days: DayConditionDto[];
  /** 마지막으로 계산을 마친 배치. */
  items: MockDraftItem[];
  /** 계산 중인 배치. `readyAt`이 지나면 `items`가 되고(`error`가 있으면 실패) 비워진다. */
  pending: { readyAt: number; items: MockDraftItem[]; error: MockJobError | null } | null;
  /** 처음 만들기에 실패했을 때만. */
  error: MockJobError | null;
  /** API-084 멱등키별 요청 본문과 작업 id. */
  adjustRequests: Map<string, { requestBody: string; jobId: string }>;
  /** API-039로 저장한 요청과 저장 뒤 여행방 version(재전송 보호). */
  applied: { requestBody: string; resultVersion: number } | null;
};

/** 계산 시간이 지난 초안을 끝난 상태로 바꾼다. 다시 계산이 실패하면 마지막 성공 내용 그대로 둔다. */
export function settleMockDraft(draft: MockScheduleDraft, now: number = Date.now()): void {
  if (draft.pending === null || now < draft.pending.readyAt) {
    return;
  }

  const { items, error } = draft.pending;
  draft.pending = null;

  if (error === null) {
    draft.items = items;
    draft.draftVersion += 1;
    return;
  }

  if (draft.draftVersion === 0) {
    draft.error = error;
  }
}

function byOrder(a: MockDraftItem, b: MockDraftItem): number {
  return (a.order ?? 0) - (b.order ?? 0);
}

/** 초안 항목을 날짜별 계산 입력으로. */
export function toMockPlannedDays(items: readonly MockDraftItem[], days: readonly DayConditionDto[]): MockPlannedDay[] {
  return days.map((day) => {
    const dayItems = items.filter((item) => {
      return item.visitDate === day.date;
    }).sort(byOrder);

    return {
      ...day,

      items: dayItems.map((item) => {
        return { id: item.itemId, roomPlaceId: item.roomPlaceId, stayMinutes: item.stayMinutes, fixed: item.fixed };
      }),
    };
  });
}

function toDraftSchedule(draft: MockScheduleDraft, places: readonly RoomPlaceDto[]): DraftScheduleDto {
  const days = toMockPlannedDays(draft.items, draft.days).map((day) => {
    const { items, ...rest } = toViewDay(calculateMockDay(day, places, draft.transportMode));

    return {
      ...rest,

      items: items.map(({ id, ...item }) => {
        return { itemId: id, ...item };
      }),
    };
  });

  const unassignedPlaces = draft.items.filter((item) => {
    return item.visitDate === null;
  }).map(({ itemId, roomPlaceId }) => {
    return { itemId, roomPlaceId };
  });

  return { days, unassignedPlaces, warnings: [], startPoint: null, endPoint: null };
}

/** API-038 응답. 계산한 내용이 없으면(처음 만드는 중·실패) `schedule`은 `null`이다. */
export function toMockDraftResponse(draft: MockScheduleDraft, places: readonly RoomPlaceDto[]): ScheduleDraftDto {
  const failed = draft.error !== null;
  const computed = draft.draftVersion > 0 && !failed;
  const status = draft.pending !== null ? "RUNNING" : failed ? "FAILED" : "SUCCEEDED";

  return {
    status,
    baseVersion: draft.baseVersion,
    draftVersion: draft.draftVersion,
    conditions: { days: draft.days, startPoint: null, endPoint: null },
    schedule: computed ? toDraftSchedule(draft, places) : null,
    transportMode: draft.transportMode,
    travelDataSource: computed ? (draft.transportMode === "CAR" ? "KAKAO_CAR" : "KAKAO_TRANSIT") : null,
    error: draft.error,
  };
}

function withOrders(items: readonly MockDraftItem[], date: string, orderedIds: readonly number[]): MockDraftItem[] {
  return items.map((item) => {
    const index = orderedIds.indexOf(item.itemId);
    return index === -1 ? item : { ...item, visitDate: date, order: index + 1 };
  });
}

function unassign(items: readonly MockDraftItem[], itemId: number): MockDraftItem[] {
  return items.map((item) => {
    return item.itemId === itemId ? { ...item, visitDate: null, order: null } : item;
  });
}

type TrimResult = { items: MockDraftItem[]; error: MockJobError | null };

/**
 * 종료 시각을 넘기는 날에서 고정하지 않은 마지막 항목부터 미배치로 옮긴다(서버 `trimOverflow`).
 * 고정 항목만 남았는데도 넘치면 SC002로 실패한다.
 */
export function trimMockOverflow(
  items: readonly MockDraftItem[],
  days: readonly DayConditionDto[],
  places: readonly RoomPlaceDto[],
  transportMode: TransportModeDto,
): TrimResult {
  let current = [...items];

  for (const day of toMockPlannedDays(current, days)) {
    let planned = day;

    while (calculateMockDay(planned, places, transportMode).exceedsEndTime) {
      const drop = [...planned.items].reverse().find((item) => {
        return !item.fixed;
      });

      if (drop === undefined) {
        return { items: current, error: { code: "SC002", message: `${day.date}: 순서를 고정한 항목만으로도 종료 시각을 넘깁니다.` } };
      }

      current = unassign(current, drop.id);

      planned = {
        ...planned,

        items: planned.items.filter((item) => {
          return item.id !== drop.id;
        }),
      };

      current = withOrders(current, day.date, planned.items.map((item) => {
        return item.id;
      }));
    }
  }

  return { items: current, error: null };
}

function nearestIndex(from: Point | null, candidates: readonly MockDraftItem[], places: readonly RoomPlaceDto[]): number {
  if (from === null) {
    return 0;
  }

  let best = 0;
  let bestDistance = Infinity;

  candidates.forEach((candidate, index) => {
    const point = pointOf(places, candidate.roomPlaceId);
    const distance = point === null ? Infinity : distanceKm(from, point);

    if (distance < bestDistance) {
      best = index;
      bestDistance = distance;
    }
  });

  return best;
}

/**
 * 처음 만드는 초안의 배치(서버 `DraftPlanner`를 단순하게 흉내 낸다).
 * 이어받은 항목은 제 날짜·순서에 두고, 나머지는 그날 시간에 비례한 수만큼 가까운 곳부터 채운다. 시간 안에 못 넣으면 미배치다.
 */
export function planMockDraftItems(
  items: readonly MockDraftItem[],
  days: readonly DayConditionDto[],
  places: readonly RoomPlaceDto[],
  transportMode: TransportModeDto,
): TrimResult {
  let current = [...items];

  let rest = current.filter((item) => {
    return item.visitDate === null;
  });

  const totalMinutes = days.reduce((sum, day) => {
    return sum + toMinutes(day.endTime) - toMinutes(day.startTime);
  }, 0);

  const freeCount = rest.length;

  for (const quotaLimited of [true, false]) {
    for (const day of days) {
      const quota = quotaLimited ? Math.ceil((freeCount * (toMinutes(day.endTime) - toMinutes(day.startTime))) / totalMinutes) : Infinity;
      let added = 0;

      while (rest.length > 0 && added < quota) {
        const planned = toMockPlannedDays(current, [day])[0];
        const last = planned.items.at(-1);
        const index = nearestIndex(last === undefined ? null : pointOf(places, last.roomPlaceId), rest, places);
        const candidate = rest[index];
        const trial = { ...planned, items: [...planned.items, { id: candidate.itemId, roomPlaceId: candidate.roomPlaceId, stayMinutes: candidate.stayMinutes, fixed: false }] };

        if (calculateMockDay(trial, places, transportMode).exceedsEndTime) {
          break;
        }

        current = withOrders(current, day.date, trial.items.map((item) => {
          return item.id;
        }));

        rest = rest.filter((item) => {
          return item.itemId !== candidate.itemId;
        });

        added += 1;
      }
    }
  }

  return trimMockOverflow(current, days, places, transportMode);
}

// #endregion
