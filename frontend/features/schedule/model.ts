import { toPlaceCategory, type PlaceCategory, type PlaceCategoryDto } from "../place/model";
import { TRANSPORT_MODES, type TransportMode } from "../room/model";
import { assertId, ContractMismatchError, toKnownEnum } from "../../lib/contract";
import { compareLocalDate, parseLocalDate, parseNullableLocalDate, parseServerDateTime, type LocalDate } from "../../lib/date";

// 가정(TBD): API-036~039의 요청·응답 상세는 명세 미확정이다(2026-11-04 Swagger paths 비어 있음).
// 필드는 Figma "여행방 일정 만들기" 섹션(W13·W14·W09-B·W14-L·L2·M·T)에서 역산했다. 명세가 나오면 이름과 형식을 맞춘다.
// - 일정이 없으면 API-036은 빈 성공 응답(data: null)을 준다.
// - 초안 생성(API-037)은 작업이다. 초안 조회(API-038)의 status로 진행을 확인한다(API-011 대신).
// - "다시 계산"도 API-037로 보낸다. 날마다 장소 순서·머무는 시간을 정해 보내면 서버는 시각과 이동 시간만 계산한다.
// - 적용(API-039)에는 초안을 만들 때의 일정 버전을 함께 보내고, 그사이 일정이 바뀌었으면 409 C008을 받는다.

export const SCHEDULE_PACES = ["RELAXED", "NORMAL", "PACKED"] as const;
export type SchedulePaceDto = (typeof SCHEDULE_PACES)[number];
export type SchedulePace = SchedulePaceDto | "UNKNOWN";

/** W13 "하루 일정" 선택지. */
export const SCHEDULE_PACE_LABEL: Readonly<Record<SchedulePaceDto, string>> = {
  RELAXED: "여유롭게",
  NORMAL: "보통",
  PACKED: "빼곡하게",
};

export const PARKING_STATUSES = ["AVAILABLE", "UNAVAILABLE"] as const;
export type ParkingStatus = (typeof PARKING_STATUSES)[number] | "UNKNOWN";

export const SCHEDULE_DRAFT_STATUSES = ["PROCESSING", "COMPLETED", "FAILED"] as const;

/** W14-T 머무는 시간 조정 단위와 범위(분). Figma 시안 값이며 확정 전이다(TBD). */
export const STAY_STEP_MINUTES = 10;
export const STAY_MIN_MINUTES = 10;
export const STAY_MAX_MINUTES = 480;

/** W14-T 빠른 선택(분). */
export const STAY_QUICK_OPTIONS: readonly number[] = [30, 60, 90, 120];

/** W14-L 일차 칩 줄에 일차 선택 버튼(📅)을 붙이는 기준 일수. */
export const DAY_PICKER_MIN_DAYS = 5;

// #region DTO

type ScheduleEndpointDto = { name: string; time: string };

export type ScheduleStopDto = {
  roomPlaceId: number;
  name: string;
  category: PlaceCategoryDto | null;
  /** 서버가 붙이는 짧은 표시(예: "점심"). 없으면 `null`. */
  tag: string | null;
  arrivalTime: string;
  departureTime: string;
  stayMinutes: number;
  /** 카테고리 기본 머무는 시간(W14-T "카페 기본값 60분"). */
  defaultStayMinutes: number;
  /** 앞 지점(출발지 또는 이전 장소)에서 오는 이동 시간. 모르면 `null`. */
  travelMinutes: number | null;
  /** 자차일 때 주차 정보. 정보가 없거나 대중교통이면 `null`. */
  parking: string | null;
};

/** 시간 안에 넣지 못한 장소. 이유 문구는 서버가 만든다. */
export type UnplacedStopDto = {
  roomPlaceId: number;
  name: string;
  category: PlaceCategoryDto | null;
  stayMinutes: number;
  reason: string;
  /** 서버가 권하는 옮길 날. 없으면 `null`. */
  suggestedDate: string | null;
};

export type ScheduleDayDto = {
  date: string;
  /** 조건으로 정한 하루 시작·종료 시각(W13). */
  startTime: string;
  endTime: string;
  /** 출발 지점과 출발 시각(예: 강릉역 10:00). */
  origin: ScheduleEndpointDto;
  /** 도착 지점, 도착 시각, 마지막 장소에서 오는 이동 시간. */
  destination: ScheduleEndpointDto & { travelMinutes: number | null };
  /** 그날 이동 시간 합. 하나라도 모르면 `null`. */
  totalTravelMinutes: number | null;
  totalStayMinutes: number;
  stops: ScheduleStopDto[];
  unplaced: UnplacedStopDto[];
};

export type ScheduleBodyDto = { transportMode: string; pace: string; days: ScheduleDayDto[] };

export type RoomScheduleDto = ScheduleBodyDto & { version: number; createdAt: string };

export type ScheduleDraftDto = {
  draftId: number;
  status: string;
  /** 실패 이유 code. 실패가 아니면 `null`. */
  failureCode: string | null;
  /** 초안을 만들 때의 여행방 일정 버전. 일정이 없었으면 `null`. 적용할 때 그대로 보낸다. */
  baseVersion: number | null;
  /** 완료되면 초안 일정. */
  schedule: ScheduleBodyDto | null;
};

export type ScheduleDraftStopRequestDto = { roomPlaceId: number; stayMinutes: number };

export type CreateScheduleDraftRequestDto = {
  pace: SchedulePaceDto;
  /** `stops`가 `null`이면 그날 장소와 순서를 AI가 정한다. 값이 있으면 그 순서·머무는 시간으로 시각만 계산한다. */
  days: { date: string; startTime: string; endTime: string; stops: ScheduleDraftStopRequestDto[] | null }[];
};

export type CreateScheduleDraftResponseDto = { draftId: number; status: string };

export type ApplyScheduleDraftRequestDto = { draftId: number; baseVersion: number | null };

// #endregion

// #region 화면 모델

/** 하루 안의 시각 `HH:mm`(24시간). 자정을 넘기는 일정은 다루지 않는다(TBD). */
export type LocalTime = string;

export type ScheduleEndpoint = { name: string; time: LocalTime };

export type ScheduleStop = {
  /** 여행방 후보 장소 id(roomPlaceId). */
  id: number;
  name: string;
  category: PlaceCategory | null;
  tag: string | null;
  arrivalTime: LocalTime;
  departureTime: LocalTime;
  stayMinutes: number;
  defaultStayMinutes: number;
  travelMinutes: number | null;
  parking: ParkingStatus | null;
};

export type UnplacedStop = {
  id: number;
  name: string;
  category: PlaceCategory | null;
  stayMinutes: number;
  reason: string;
  suggestedDate: LocalDate | null;
};

export type ScheduleDay = {
  date: LocalDate;
  startTime: LocalTime;
  endTime: LocalTime;
  origin: ScheduleEndpoint;
  destination: ScheduleEndpoint & { travelMinutes: number | null };
  totalTravelMinutes: number | null;
  totalStayMinutes: number;
  stops: ScheduleStop[];
  unplaced: UnplacedStop[];
};

export type ScheduleBody = { transportMode: TransportMode; pace: SchedulePace; days: ScheduleDay[] };

export type RoomSchedule = ScheduleBody & { version: number; createdAt: Date };

type ScheduleDraftBase = { id: number; baseVersion: number | null };

export type ScheduleDraft =
  | (ScheduleDraftBase & { status: "PROCESSING" })
  | (ScheduleDraftBase & { status: "COMPLETED"; schedule: ScheduleBody })
  | (ScheduleDraftBase & { status: "FAILED"; failureCode: string | null })
  | (ScheduleDraftBase & { status: "UNKNOWN" });

/** 일정에서 빠지는 장소처럼 이름만 필요한 장소. */
export type ScheduleStopRef = { id: number; name: string };

// #endregion

// #region 시각

const LOCAL_TIME_PATTERN = /^([01]\d|2[0-3]):([0-5]\d)$/;
const MINUTES_PER_HOUR = 60;
const MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

export function parseLocalTime(value: unknown, field: string): LocalTime {
  if (typeof value !== "string" || !LOCAL_TIME_PATTERN.test(value)) {
    throw new ContractMismatchError(field);
  }

  return value;
}

export function toMinutesOfDay(time: LocalTime): number {
  const [hours, minutes] = time.split(":").map(Number);
  return hours * MINUTES_PER_HOUR + minutes;
}

/** 하루 안의 분을 `HH:mm`으로. 범위를 벗어나면 하루 안으로 자른다. */
export function fromMinutesOfDay(minutes: number): LocalTime {
  const clamped = Math.min(Math.max(Math.round(minutes), 0), MINUTES_PER_DAY - 1);
  const hours = Math.floor(clamped / MINUTES_PER_HOUR);
  return `${String(hours).padStart(2, "0")}:${String(clamped % MINUTES_PER_HOUR).padStart(2, "0")}`;
}

/** `50분`, `1시간`, `1시간 30분`. */
export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / MINUTES_PER_HOUR);
  const rest = minutes % MINUTES_PER_HOUR;

  if (hours === 0) {
    return `${rest}분`;
  }

  return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`;
}

/** 시작 시각부터 `stepMinutes` 간격의 선택지. 끝 시각을 포함한다. */
export function buildTimeOptions(from: LocalTime, to: LocalTime, stepMinutes: number): LocalTime[] {
  const options: LocalTime[] = [];

  for (let minutes = toMinutesOfDay(from); minutes <= toMinutesOfDay(to); minutes += stepMinutes) {
    options.push(fromMinutesOfDay(minutes));
  }

  return options;
}

// #endregion

// #region 변환

function assertMinutes(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function toNullableMinutes(value: unknown, field: string): number | null {
  return value === null ? null : assertMinutes(value, field);
}

function assertVersion(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function toScheduleStop(dto: ScheduleStopDto): ScheduleStop {
  return {
    id: assertId(dto.roomPlaceId, "stops.roomPlaceId"),
    name: dto.name,
    category: toPlaceCategory(dto.category),
    tag: dto.tag,
    arrivalTime: parseLocalTime(dto.arrivalTime, "stops.arrivalTime"),
    departureTime: parseLocalTime(dto.departureTime, "stops.departureTime"),
    stayMinutes: assertMinutes(dto.stayMinutes, "stops.stayMinutes"),
    defaultStayMinutes: assertMinutes(dto.defaultStayMinutes, "stops.defaultStayMinutes"),
    travelMinutes: toNullableMinutes(dto.travelMinutes, "stops.travelMinutes"),
    parking: dto.parking === null ? null : toKnownEnum(dto.parking, PARKING_STATUSES),
  };
}

function toUnplacedStop(dto: UnplacedStopDto): UnplacedStop {
  return {
    id: assertId(dto.roomPlaceId, "unplaced.roomPlaceId"),
    name: dto.name,
    category: toPlaceCategory(dto.category),
    stayMinutes: assertMinutes(dto.stayMinutes, "unplaced.stayMinutes"),
    reason: dto.reason,
    suggestedDate: parseNullableLocalDate(dto.suggestedDate, "unplaced.suggestedDate"),
  };
}

function toScheduleDay(dto: ScheduleDayDto): ScheduleDay {
  return {
    date: parseLocalDate(dto.date, "days.date"),
    startTime: parseLocalTime(dto.startTime, "days.startTime"),
    endTime: parseLocalTime(dto.endTime, "days.endTime"),
    origin: { name: dto.origin.name, time: parseLocalTime(dto.origin.time, "days.origin.time") },

    destination: {
      name: dto.destination.name,
      time: parseLocalTime(dto.destination.time, "days.destination.time"),
      travelMinutes: toNullableMinutes(dto.destination.travelMinutes, "days.destination.travelMinutes"),
    },

    totalTravelMinutes: toNullableMinutes(dto.totalTravelMinutes, "days.totalTravelMinutes"),
    totalStayMinutes: assertMinutes(dto.totalStayMinutes, "days.totalStayMinutes"),
    stops: dto.stops.map(toScheduleStop),
    unplaced: dto.unplaced.map(toUnplacedStop),
  };
}

export function toScheduleBody(dto: ScheduleBodyDto): ScheduleBody {
  return {
    transportMode: toKnownEnum(dto.transportMode, TRANSPORT_MODES),
    pace: toKnownEnum(dto.pace, SCHEDULE_PACES),

    days: dto.days.map(toScheduleDay).sort((a, b) => {
      return compareLocalDate(a.date, b.date);
    }),
  };
}

export function toRoomSchedule(dto: RoomScheduleDto): RoomSchedule {
  return {
    ...toScheduleBody(dto),
    version: assertVersion(dto.version, "version"),
    createdAt: parseServerDateTime(dto.createdAt, "createdAt"),
  };
}

export function toScheduleDraft(dto: ScheduleDraftDto): ScheduleDraft {
  const base = {
    id: assertId(dto.draftId, "draftId"),
    baseVersion: dto.baseVersion === null ? null : assertVersion(dto.baseVersion, "baseVersion"),
  };

  const status = toKnownEnum(dto.status, SCHEDULE_DRAFT_STATUSES);

  switch (status) {
    case "PROCESSING":
      return { ...base, status };
    case "COMPLETED":
      if (dto.schedule === null) {
        throw new ContractMismatchError("schedule");
      }

      return { ...base, status, schedule: toScheduleBody(dto.schedule) };
    case "FAILED":
      return { ...base, status, failureCode: dto.failureCode };
    default:
      return { ...base, status: "UNKNOWN" };
  }
}

/** 초안이 더 바뀌지 않는 상태인지. 이 상태가 되면 진행 확인을 멈춘다. */
export function isScheduleDraftFinished(draft: ScheduleDraft | undefined): boolean {
  return draft !== undefined && draft.status !== "PROCESSING";
}

// #endregion

// #region 화면 계산

/** 새 기간 밖으로 빠지는 날의 장소(넣지 못한 장소 포함). 기간이 없으면(날짜 미정) 모든 장소가 빠진다. */
export function getPlacesOutsideRange(schedule: ScheduleBody, range: { startDate: LocalDate; endDate: LocalDate } | null): ScheduleStopRef[] {
  return schedule.days
    .filter((day) => {
      return range === null || compareLocalDate(day.date, range.startDate) < 0 || compareLocalDate(day.date, range.endDate) > 0;
    })
    .flatMap(getDayStopRefs);
}

/** 그날 일정에 들어간 장소와 넣지 못한 장소. */
export function getDayStopRefs(day: Pick<ScheduleDay, "stops" | "unplaced">): ScheduleStopRef[] {
  return [...day.stops, ...day.unplaced].map((stop) => {
    return { id: stop.id, name: stop.name };
  });
}

export function hasUnplacedStops(day: Pick<ScheduleDay, "unplaced">): boolean {
  return day.unplaced.length > 0;
}

/** 초안을 적용하면 지금 일정에서 달라지는 점. */
export type ScheduleChanges = {
  /** 넣은 장소나 순서가 바뀌는 날(초안 기준). */
  changedDates: LocalDate[];
  /** 지금 일정에 있는데 초안 일정에는 없는 장소(넣지 못한 장소 포함). */
  droppedStops: ScheduleStopRef[];
};

function stopIdsOf(day: { stops: readonly { id: number }[] }): number[] {
  return day.stops.map((stop) => {
    return stop.id;
  });
}

function isSameOrder(a: readonly number[], b: readonly number[]): boolean {
  return a.length === b.length && a.every((id, index) => {
    return id === b[index];
  });
}

/** 지금 일정과 초안을 날짜별로 비교한다(ScheduleTimeline 체크리스트 "현재 일정과 달라지는 점"). */
export function getScheduleChanges(current: Pick<ScheduleBody, "days">, draft: Pick<ScheduleBody, "days">): ScheduleChanges {
  const currentOrders = new Map(current.days.map((day) => {
    return [day.date, stopIdsOf(day)];
  }));

  const draftStopIds = new Set(draft.days.flatMap(stopIdsOf));

  const changedDates = draft.days.filter((day) => {
    return !isSameOrder(currentOrders.get(day.date) ?? [], stopIdsOf(day));
  }).map((day) => {
    return day.date;
  });

  const droppedStops = current.days.flatMap((day) => {
    return day.stops;
  }).filter((stop) => {
    return !draftStopIds.has(stop.id);
  }).map((stop) => {
    return { id: stop.id, name: stop.name };
  });

  return { changedDates, droppedStops };
}

/** 주차 줄 문구. 정보가 없으면 "주차 정보 없음"으로 둔다(명세 §5). */
export function getParkingLabel(parking: ParkingStatus | null): string {
  switch (parking) {
    case "AVAILABLE":
      return "주차 가능";
    case "UNAVAILABLE":
      return "주차 불가";
    default:
      return "주차 정보 없음";
  }
}

/** 이동 구간 문구. `자차 15분`, 모르면 `자차 · 이동 시간 정보 없음`. */
export function getTravelLabel(modeLabel: string, minutes: number | null): string {
  return minutes === null ? `${modeLabel} · 이동 시간 정보 없음` : `${modeLabel} ${formatDuration(minutes)}`;
}

// #endregion

// #region 일정 조건(W13)과 다시 계산(W14)

export type DayTimeRange = { date: LocalDate; startTime: LocalTime; endTime: LocalTime };

/** W13 시각 기본값. Figma W13 시안처럼 첫날 10:00, 다른 날 09:00에 시작하고, 여러 날이면 마지막 날은 13:00에 끝난다(TBD). */
export function getDefaultDayTimes(dates: readonly LocalDate[]): DayTimeRange[] {
  return dates.map((date, index) => {
    const isFirst = index === 0;
    const isLastOfMany = dates.length > 1 && index === dates.length - 1;
    return { date, startTime: isFirst ? "10:00" : "09:00", endTime: isLastOfMany ? "13:00" : "18:00" };
  });
}

export function isValidDayTimeRange(day: Pick<DayTimeRange, "startTime" | "endTime">): boolean {
  return toMinutesOfDay(day.startTime) < toMinutesOfDay(day.endTime);
}

/** W13 "일정 초안 만들기". 장소와 순서는 AI가 정한다. */
export function toCreateDraftRequest(pace: SchedulePaceDto, days: readonly DayTimeRange[]): CreateScheduleDraftRequestDto {
  return {
    pace,

    days: days.map((day) => {
      return { date: day.date, startTime: day.startTime, endTime: day.endTime, stops: null };
    }),
  };
}

/** W14에서 고치는 일정의 뼈대. 시각은 서버가 다시 계산하므로 장소 순서와 머무는 시간만 둔다. */
export type ScheduleLayoutStop = { id: number; name: string; stayMinutes: number };
export type ScheduleLayoutDay = DayTimeRange & { stops: ScheduleLayoutStop[] };

/** 받은 일정을 고칠 수 있는 뼈대로. 넣지 못한 장소는 그날 끝에 두어, 다시 계산해도 빠지지 않게 한다. */
export function toScheduleLayout(schedule: ScheduleBody): ScheduleLayoutDay[] {
  return schedule.days.map((day) => {
    return {
      date: day.date,
      startTime: day.startTime,
      endTime: day.endTime,

      stops: [...day.stops, ...day.unplaced].map((stop) => {
        return { id: stop.id, name: stop.name, stayMinutes: stop.stayMinutes };
      }),
    };
  });
}

function findLayoutStop(layout: readonly ScheduleLayoutDay[], stopId: number): ScheduleLayoutStop | undefined {
  return layout.flatMap((day) => {
    return day.stops;
  }).find((stop) => {
    return stop.id === stopId;
  });
}

/** 장소를 다른 날 끝으로 옮긴다(W14-M). 같은 날이거나 없는 장소면 그대로 돌려준다. */
export function moveStopToDay(layout: readonly ScheduleLayoutDay[], stopId: number, toDate: LocalDate): ScheduleLayoutDay[] {
  const stop = findLayoutStop(layout, stopId);

  const fromDay = layout.find((day) => {
    return day.stops.some((item) => {
      return item.id === stopId;
    });
  });

  if (stop === undefined || fromDay === undefined || fromDay.date === toDate) {
    return [...layout];
  }

  return layout.map((day) => {
    if (day.date === fromDay.date) {
      return {
        ...day,

        stops: day.stops.filter((item) => {
          return item.id !== stopId;
        }),
      };
    }

    return day.date === toDate ? { ...day, stops: [...day.stops, stop] } : day;
  });
}

/** 그날 안에서 장소를 한 칸 앞(-1)이나 뒤(1)로 옮긴다. 끝에서 더 갈 수 없으면 그대로 돌려준다. */
export function moveStopWithinDay(layout: readonly ScheduleLayoutDay[], stopId: number, direction: -1 | 1): ScheduleLayoutDay[] {
  return layout.map((day) => {
    const index = day.stops.findIndex((stop) => {
      return stop.id === stopId;
    });

    const target = index + direction;

    if (index === -1 || target < 0 || target >= day.stops.length) {
      return day;
    }

    const stops = [...day.stops];
    [stops[index], stops[target]] = [stops[target], stops[index]];
    return { ...day, stops };
  });
}

/** 머무는 시간을 바꾼다(W14-T). 범위를 벗어나면 범위 안으로 자른다. */
export function setStopStay(layout: readonly ScheduleLayoutDay[], stopId: number, stayMinutes: number): ScheduleLayoutDay[] {
  const minutes = clampStayMinutes(stayMinutes);

  return layout.map((day) => {
    return {
      ...day,

      stops: day.stops.map((stop) => {
        return stop.id === stopId ? { ...stop, stayMinutes: minutes } : stop;
      }),
    };
  });
}

/** 일정에서 빼고 후보 장소로 돌려보낸다(W14-M 보조 동작). */
export function removeStop(layout: readonly ScheduleLayoutDay[], stopId: number): ScheduleLayoutDay[] {
  return layout.map((day) => {
    return {
      ...day,

      stops: day.stops.filter((stop) => {
        return stop.id !== stopId;
      }),
    };
  });
}

/** 날마다 장소와 순서가 같은지. "순서 바꾸기"에서 바뀐 것이 있는지 볼 때 쓴다. */
export function isSameLayoutOrder(a: readonly ScheduleLayoutDay[], b: readonly ScheduleLayoutDay[]): boolean {
  return a.length === b.length && a.every((day, index) => {
    const other = b.at(index);
    return other !== undefined && day.date === other.date && isSameOrder(stopIdsOf(day), stopIdsOf(other));
  });
}

export function clampStayMinutes(minutes: number): number {
  const stepped = Math.round(minutes / STAY_STEP_MINUTES) * STAY_STEP_MINUTES;
  return Math.min(Math.max(stepped, STAY_MIN_MINUTES), STAY_MAX_MINUTES);
}

/** W14 "다시 계산". 날마다 정한 순서와 머무는 시간을 그대로 보낸다. */
export function toRecalculationRequest(pace: SchedulePaceDto, layout: readonly ScheduleLayoutDay[]): CreateScheduleDraftRequestDto {
  return {
    pace,

    days: layout.map((day) => {
      return {
        date: day.date,
        startTime: day.startTime,
        endTime: day.endTime,

        stops: day.stops.map((stop) => {
          return { roomPlaceId: stop.id, stayMinutes: stop.stayMinutes };
        }),
      };
    }),
  };
}

/** 다시 보낼 pace. 모르는 값이면 기본 "보통"으로 보낸다. */
export function toPaceRequest(pace: SchedulePace): SchedulePaceDto {
  return pace === "UNKNOWN" ? "NORMAL" : pace;
}

// #endregion
