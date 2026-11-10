import { TRANSPORT_MODES, type RoomPlace, type TransportMode } from "../room/model";
import { assertId, ContractMismatchError, toKnownEnum } from "../../lib/contract";
import { addDays, compareLocalDate, diffDays, formatServerDateTime, parseLocalDate, parseServerDateTime, type LocalDate } from "../../lib/date";

// 일정(API-036~039·084) DTO는 서버 계약을 따른다. backend/dev의 schedule dto·draft 서비스와 backend/docs/06-schedule.md(2026-11-10)와 대조했다.
// - 흐름: 초안 생성(037, 작업) → 초안 조회(038) → 화면에서 고치기(서버 호출 없음) → 다시 계산(084, 선택) → 저장(039).
// - 서버는 장소 이름·카테고리를 주지 않는다. `roomPlaceId`로 여행방 후보 목록과 묶는다.
// - 도착·출발 시각과 이동 시간은 서버가 계산한 값만 쓴다. 서버가 모르면 `null`이고 화면에서 만들지 않는다.

export const SCHEDULE_WARNINGS = ["TRAVEL_TIME_UNAVAILABLE", "ARRIVAL_TIME_UNKNOWN", "END_TIME_EXCEEDED"] as const;
export type ScheduleWarning = (typeof SCHEDULE_WARNINGS)[number] | "UNKNOWN";

export const SCHEDULE_DRAFT_STATUSES = ["QUEUED", "RUNNING", "SUCCEEDED", "FAILED"] as const;

/** W14-T 머무는 시간 조정 단위(분). 서버는 1분 단위로 받으므로 받은 값을 이 단위로 맞추지 않는다. */
export const STAY_STEP_MINUTES = 10;

/** 서버가 받는 머무는 시간 범위(분). 0분·720분 초과는 C003이다. */
export const STAY_MIN_MINUTES = 1;
export const STAY_MAX_MINUTES = 720;

/** 서버가 새로 배치하는 항목에 쓰는 머무는 시간(분). 미배치 항목은 초안 조회에 머무는 시간이 없어 다시 넣을 때 이 값을 보낸다. */
export const DEFAULT_STAY_MINUTES = 60;

/** W14-T 빠른 선택(분). */
export const STAY_QUICK_OPTIONS: readonly number[] = [30, 60, 90, 120];

/** W14-L 일차 칩 줄에 일차 선택 버튼(📅)을 붙이는 기준 일수. */
export const DAY_PICKER_MIN_DAYS = 5;

// #region DTO

/** 첫날 출발지·마지막 날 도착지. 이름은 없을 수 있다. */
export type SchedulePointDto = { name: string | null; latitude: number; longitude: number };

/** 이동 구간. `fromItemId`가 `null`이면 첫날 출발지에서, `toItemId`가 `null`이면 마지막 날 도착지로 가는 구간이다. */
export type ScheduleLegDto = {
  fromItemId: number | null;
  toItemId: number | null;
  transportMode: string;
  travelMinutes: number | null;
  source: string | null;
};

type ScheduleItemFieldsDto = {
  roomPlaceId: number;
  order: number;
  /** datetime(`2026-11-08 10:00:00+09`). 서버가 계산하지 못했으면 `null`. */
  arrivalAt: string | null;
  departureAt: string | null;
  stayMinutes: number;
  fixed: boolean;
};

export type ScheduleItemDto = ScheduleItemFieldsDto & { id: number };

/** 초안 항목. `itemId`는 초안 안에서만 쓰는 번호다. */
export type DraftItemDto = ScheduleItemFieldsDto & { itemId: number };

type ScheduleDayFieldsDto = {
  date: string;
  /** `HH:mm:ss`. 일차 조건이 없으면 `null`. */
  startTime: string | null;
  endTime: string | null;
  legs: ScheduleLegDto[];
  warnings: string[];
};

export type ScheduleDayDto = ScheduleDayFieldsDto & { items: ScheduleItemDto[] };
export type DraftDayDto = ScheduleDayFieldsDto & { items: DraftItemDto[] };

type ScheduleViewFieldsDto = { warnings: string[]; startPoint: SchedulePointDto | null; endPoint: SchedulePointDto | null };

/** 확정 일정(API-036 `schedule`). API-022·023·039 응답의 `schedule`도 같은 모양이다. */
export type ScheduleViewDto = ScheduleViewFieldsDto & { days: ScheduleDayDto[]; unassignedPlaces: { roomPlaceId: number }[] };

/** API-036·039 응답. 확정 항목이 없으면 `schedule`이 `null`이다. `version`은 여행방 version이다. */
export type ScheduleResponseDto = { schedule: ScheduleViewDto | null; version: number };

export type DraftScheduleDto = ScheduleViewFieldsDto & { days: DraftDayDto[]; unassignedPlaces: { itemId: number; roomPlaceId: number }[] };

/** 일차 조건. 시각은 `HH:mm:ss`다. */
export type DayConditionDto = { date: string; startTime: string; endTime: string };

export type DraftConditionsDto = { days: DayConditionDto[]; startPoint: SchedulePointDto | null; endPoint: SchedulePointDto | null };

/** API-038 응답. 다시 계산하는 중(RUNNING)에는 `schedule`이 마지막으로 계산을 마친 내용이다. */
export type ScheduleDraftDto = {
  status: string;
  baseVersion: number;
  draftVersion: number;
  conditions: DraftConditionsDto;
  schedule: DraftScheduleDto | null;
  transportMode: string;
  travelDataSource: string | null;
  /** 처음 만들기에 실패했을 때만 있다. */
  error: { code: string; message: string } | null;
};

/** API-037·084 응답(202). */
export type DraftJobResponseDto = { jobId: string; draftKey: string; status: string };

/** 저장된 일정에서 새 초안이 이어받을 항목(API-037 `fixedItems`). */
export type FixedItemDto = { roomPlaceId: number; visitDate: string; order: number; stayMinutes: number; fixed: boolean };

export type CreateScheduleDraftRequestDto = {
  /** 여행방 version. */
  baseVersion: number;
  /** 지금 빼지 않은 후보 전부. */
  roomPlaceIds: number[];
  /** 여행의 모든 날짜. */
  days: DayConditionDto[];
  startPoint?: SchedulePointDto | null;
  endPoint?: SchedulePointDto | null;
  /** 후보별 머무는 시간(`{"후보 id": 분}`). 없는 후보는 서버 기본값(60분)이다. */
  stayMinutes?: Record<string, number>;
  fixedItems?: FixedItemDto[];
};

/** API-084 요청의 항목. `fixed`를 생략하면 지금 값을 유지한다. */
export type AdjustDraftItemDto = { itemId: number; roomPlaceId: number; visitDate: string; order: number; stayMinutes: number };

/** API-084 요청. 바꿀 항목과 뺄 항목만 보낸다. */
export type AdjustScheduleDraftRequestDto = {
  baseVersion: number;
  draftVersion: number;
  items: AdjustDraftItemDto[];
  removeItemIds: number[];
};

export type SaveScheduleItemDto = { itemId: number; roomPlaceId: number; order: number; stayMinutes: number; fixed: boolean };

/** API-039 요청. 변경분이 아니라 고치기를 마친 전체 상태다. `startPoint`·`endPoint`는 키가 있어야 한다. */
export type SaveScheduleRequestDto = {
  draftKey: string;
  draftVersion: number;
  baseVersion: number;
  days: (DayConditionDto & { items: SaveScheduleItemDto[] })[];
  unassignedItemIds: number[];
  startPoint: SchedulePointDto | null;
  endPoint: SchedulePointDto | null;
};

// #endregion

// #region 화면 모델

/** 하루 안의 시각 `HH:mm`(24시간). 서버 시간대(Asia/Seoul) 기준이다. */
export type LocalTime = string;

export type SchedulePoint = { name: string | null; latitude: number; longitude: number };

export type ScheduleLeg = {
  fromItemId: number | null;
  toItemId: number | null;
  transportMode: TransportMode;
  /** 서버가 이동 시간을 구하지 못했으면 `null`. */
  travelMinutes: number | null;
};

export type ScheduleItem = {
  /** 확정 일정은 항목 id, 초안은 초안 안의 `itemId`. */
  id: number;
  roomPlaceId: number;
  arrivalTime: LocalTime | null;
  departureTime: LocalTime | null;
  stayMinutes: number;
  fixed: boolean;
};

export type ScheduleDay = {
  date: LocalDate;
  startTime: LocalTime | null;
  endTime: LocalTime | null;
  /** 방문 순서대로. */
  items: ScheduleItem[];
  legs: ScheduleLeg[];
  warnings: ScheduleWarning[];
};

type ScheduleBase = { days: ScheduleDay[]; warnings: ScheduleWarning[]; startPoint: SchedulePoint | null; endPoint: SchedulePoint | null };

/** 여행방에 저장된 확정 일정. */
export type RoomSchedule = ScheduleBase & {
  /** 일정에 넣지 않은 후보(roomPlaceId). */
  unassignedPlaceIds: number[];
};

/** API-039 저장 결과. `version`은 저장한 뒤의 여행방 version이다. */
export type SavedSchedule = { schedule: RoomSchedule | null; version: number };

export type DraftUnassignedItem = { itemId: number; roomPlaceId: number };

export type DraftSchedule = ScheduleBase & { unassigned: DraftUnassignedItem[] };

export type DayTimeRange = { date: LocalDate; startTime: LocalTime; endTime: LocalTime };

type ScheduleDraftBase = {
  /** 초안을 계산한 기준 여행방 version. 다시 계산·저장할 때 그대로 보낸다. */
  baseVersion: number;
  /** 계산을 마칠 때마다 1씩 오른다. */
  draftVersion: number;
  transportMode: TransportMode;
  conditions: DayTimeRange[];
  startPoint: SchedulePoint | null;
  endPoint: SchedulePoint | null;
};

export type ScheduleDraft =
  | (ScheduleDraftBase & { status: "PROCESSING" })
  | (ScheduleDraftBase & { status: "READY"; schedule: DraftSchedule; recalculating: boolean })
  | (ScheduleDraftBase & { status: "FAILED"; errorCode: string | null })
  | (ScheduleDraftBase & { status: "UNKNOWN" });

/** 계산을 마친 내용이 있는 초안. 다시 계산하는 중이면 `recalculating`이 `true`이고 내용은 마지막 계산 결과다. */
export type ReadyScheduleDraft = Extract<ScheduleDraft, { status: "READY" }>;

/** API-037·084가 만든 작업. */
export type ScheduleDraftJob = { jobId: string; draftKey: string };

// #endregion

// #region 시각

const SERVER_TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$/;
const MINUTES_PER_HOUR = 60;
const MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;
const TIME_OF_DAY_START = 11;
const TIME_OF_DAY_END = 16;
const LOCAL_TIME_LENGTH = 5;

/** 계약의 time(`HH:mm:ss`)을 화면 시각(`HH:mm`)으로. */
function parseServerTime(value: unknown, field: string): LocalTime {
  if (typeof value !== "string" || !SERVER_TIME_PATTERN.test(value)) {
    throw new ContractMismatchError(field);
  }

  return value.slice(0, LOCAL_TIME_LENGTH);
}

/** 화면 시각(`HH:mm`)을 계약의 time(`HH:mm:ss`)으로. */
function toServerTime(time: LocalTime): string {
  return `${time}:00`;
}

/** 계약의 datetime에서 서버 시간대(Asia/Seoul)의 시각(`HH:mm`)만 꺼낸다. 기기 시간대와 상관없다. */
function toSeoulTimeOfDay(value: unknown, field: string): LocalTime | null {
  return value === null ? null : formatServerDateTime(parseServerDateTime(value, field)).slice(TIME_OF_DAY_START, TIME_OF_DAY_END);
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

function assertCount(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function assertList<T>(value: T[], field: string): T[] {
  if (!Array.isArray(value)) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function toWarnings(values: string[], field: string): ScheduleWarning[] {
  return assertList(values, field).map((value) => {
    return toKnownEnum(value, SCHEDULE_WARNINGS);
  });
}

function toSchedulePoint(dto: SchedulePointDto | null, field: string): SchedulePoint | null {
  if (dto === null) {
    return null;
  }

  if (!Number.isFinite(dto.latitude) || !Number.isFinite(dto.longitude)) {
    throw new ContractMismatchError(field);
  }

  return { name: dto.name, latitude: dto.latitude, longitude: dto.longitude };
}

function toScheduleLeg(dto: ScheduleLegDto): ScheduleLeg {
  return {
    fromItemId: dto.fromItemId === null ? null : assertId(dto.fromItemId, "legs.fromItemId"),
    toItemId: dto.toItemId === null ? null : assertId(dto.toItemId, "legs.toItemId"),
    transportMode: toKnownEnum(dto.transportMode, TRANSPORT_MODES),
    travelMinutes: dto.travelMinutes === null ? null : assertCount(dto.travelMinutes, "legs.travelMinutes"),
  };
}

function toScheduleItem(dto: ScheduleItemFieldsDto, id: number): ScheduleItem {
  return {
    id: assertId(id, "items.id"),
    roomPlaceId: assertId(dto.roomPlaceId, "items.roomPlaceId"),
    arrivalTime: toSeoulTimeOfDay(dto.arrivalAt, "items.arrivalAt"),
    departureTime: toSeoulTimeOfDay(dto.departureAt, "items.departureAt"),
    stayMinutes: assertCount(dto.stayMinutes, "items.stayMinutes"),
    fixed: dto.fixed,
  };
}

function byOrder(a: { order: number }, b: { order: number }): number {
  return a.order - b.order;
}

function toScheduleDay(dto: ScheduleDayFieldsDto, items: ScheduleItem[]): ScheduleDay {
  return {
    date: parseLocalDate(dto.date, "days.date"),
    startTime: dto.startTime === null ? null : parseServerTime(dto.startTime, "days.startTime"),
    endTime: dto.endTime === null ? null : parseServerTime(dto.endTime, "days.endTime"),
    items,
    legs: assertList(dto.legs, "days.legs").map(toScheduleLeg),
    warnings: toWarnings(dto.warnings, "days.warnings"),
  };
}

function byDate(a: { date: LocalDate }, b: { date: LocalDate }): number {
  return compareLocalDate(a.date, b.date);
}

function toScheduleBase(dto: ScheduleViewFieldsDto, days: ScheduleDay[]): ScheduleBase {
  return {
    days: days.sort(byDate),
    warnings: toWarnings(dto.warnings, "warnings"),
    startPoint: toSchedulePoint(dto.startPoint, "startPoint"),
    endPoint: toSchedulePoint(dto.endPoint, "endPoint"),
  };
}

function toRoomSchedule(dto: ScheduleViewDto): RoomSchedule {
  const days = assertList(dto.days, "days").map((day) => {
    return toScheduleDay(day, [...assertList(day.items, "days.items")].sort(byOrder).map((item) => {
      return toScheduleItem(item, item.id);
    }));
  });

  return {
    ...toScheduleBase(dto, days),

    unassignedPlaceIds: assertList(dto.unassignedPlaces, "unassignedPlaces").map((place) => {
      return assertId(place.roomPlaceId, "unassignedPlaces.roomPlaceId");
    }),
  };
}

/** API-036·039 응답. 확정 항목이 없으면 일정은 `null`이다. */
export function toSavedSchedule(dto: ScheduleResponseDto): SavedSchedule {
  return { schedule: dto.schedule === null ? null : toRoomSchedule(dto.schedule), version: assertCount(dto.version, "version") };
}

function toDraftSchedule(dto: DraftScheduleDto): DraftSchedule {
  const days = assertList(dto.days, "schedule.days").map((day) => {
    return toScheduleDay(day, [...assertList(day.items, "schedule.days.items")].sort(byOrder).map((item) => {
      return toScheduleItem(item, item.itemId);
    }));
  });

  return {
    ...toScheduleBase(dto, days),

    unassigned: assertList(dto.unassignedPlaces, "schedule.unassignedPlaces").map((place) => {
      return { itemId: assertId(place.itemId, "unassignedPlaces.itemId"), roomPlaceId: assertId(place.roomPlaceId, "unassignedPlaces.roomPlaceId") };
    }),
  };
}

function toDayTimeRange(dto: DayConditionDto): DayTimeRange {
  return {
    date: parseLocalDate(dto.date, "conditions.days.date"),
    startTime: parseServerTime(dto.startTime, "conditions.days.startTime"),
    endTime: parseServerTime(dto.endTime, "conditions.days.endTime"),
  };
}

export function toScheduleDraft(dto: ScheduleDraftDto): ScheduleDraft {
  const base: ScheduleDraftBase = {
    baseVersion: assertCount(dto.baseVersion, "baseVersion"),
    draftVersion: assertCount(dto.draftVersion, "draftVersion"),
    transportMode: toKnownEnum(dto.transportMode, TRANSPORT_MODES),
    conditions: assertList(dto.conditions.days, "conditions.days").map(toDayTimeRange).sort(byDate),
    startPoint: toSchedulePoint(dto.conditions.startPoint, "conditions.startPoint"),
    endPoint: toSchedulePoint(dto.conditions.endPoint, "conditions.endPoint"),
  };

  const status = toKnownEnum(dto.status, SCHEDULE_DRAFT_STATUSES);

  if (status === "UNKNOWN") {
    return { ...base, status };
  }

  if (status === "FAILED") {
    return { ...base, status, errorCode: dto.error?.code ?? null };
  }

  if (dto.schedule === null) {
    if (status === "SUCCEEDED") {
      throw new ContractMismatchError("schedule");
    }

    return { ...base, status: "PROCESSING" };
  }

  return { ...base, status: "READY", schedule: toDraftSchedule(dto.schedule), recalculating: status !== "SUCCEEDED" };
}

export function toScheduleDraftJob(dto: DraftJobResponseDto): ScheduleDraftJob {
  if (typeof dto.jobId !== "string" || dto.jobId === "") {
    throw new ContractMismatchError("jobId");
  }

  if (typeof dto.draftKey !== "string" || dto.draftKey === "") {
    throw new ContractMismatchError("draftKey");
  }

  return { jobId: dto.jobId, draftKey: dto.draftKey };
}

/** 초안이 계산을 기다리지 않는 상태인지. 이 상태가 되면 진행 확인을 멈춘다. */
export function isScheduleDraftSettled(draft: ScheduleDraft | undefined): boolean {
  if (draft === undefined || draft.status === "PROCESSING") {
    return false;
  }

  return draft.status !== "READY" || !draft.recalculating;
}

// #endregion

// #region 화면 계산

const UNKNOWN_PLACE_NAME = "이름을 확인할 수 없는 장소";

/** `roomPlaceId`로 찾는 여행방 후보. 일정 응답에는 장소 이름·카테고리가 없다. */
export type SchedulePlaceLookup = ReadonlyMap<number, Pick<RoomPlace, "name" | "category">>;

export function toPlaceLookup(places: readonly RoomPlace[]): SchedulePlaceLookup {
  return new Map(places.map((place) => {
    return [place.id, place];
  }));
}

export function getPlaceName(places: SchedulePlaceLookup, roomPlaceId: number): string {
  return places.get(roomPlaceId)?.name ?? UNKNOWN_PLACE_NAME;
}

/** 그날 머무는 시간의 합(분). */
export function getTotalStayMinutes(items: readonly { stayMinutes: number }[]): number {
  return items.reduce((sum, item) => {
    return sum + item.stayMinutes;
  }, 0);
}

/** 그날 이동 시간의 합(분). 서버가 이동 시간을 모르는 구간이 하나라도 있으면 `null`이다. */
export function getTotalTravelMinutes(legs: readonly ScheduleLeg[]): number | null {
  let total = 0;

  for (const leg of legs) {
    if (leg.travelMinutes === null) {
      return null;
    }

    total += leg.travelMinutes;
  }

  return total;
}

/** 이동 구간 문구. `자차 15분`, 모르면 `자차 · 이동 시간 정보 없음`. */
export function getTravelLabel(modeLabel: string, minutes: number | null): string {
  return minutes === null ? `${modeLabel} · 이동 시간 정보 없음` : `${modeLabel} ${formatDuration(minutes)}`;
}

/** `10:00 – 11:00`. 서버가 시각을 모르면 `시각 정보 없음`. */
export function getItemTimeLabel(item: Pick<ScheduleItem, "arrivalTime" | "departureTime">): string {
  if (item.arrivalTime === null) {
    return "시각 정보 없음";
  }

  return item.departureTime === null ? item.arrivalTime : `${item.arrivalTime} – ${item.departureTime}`;
}

/** 서버 경고 코드의 사용자 문구. */
export function getScheduleWarningMessage(warning: ScheduleWarning): string {
  switch (warning) {
    case "TRAVEL_TIME_UNAVAILABLE":
      return "이동 시간을 확인하지 못한 구간이 있어요";
    case "ARRIVAL_TIME_UNKNOWN":
      return "도착 시각을 알 수 없는 장소가 있어요";
    case "END_TIME_EXCEEDED":
      return "마지막 일정이 이날 종료 시각을 넘어요";
    default:
      return "확인이 필요한 내용이 있어요";
  }
}

// #endregion

// #region 일정 조건(W13)과 초안 요청

/** 여행 첫날부터 마지막 날까지의 날짜. */
export function getTripDates(startDate: LocalDate, endDate: LocalDate): LocalDate[] {
  return Array.from({ length: diffDays(startDate, endDate) + 1 }, (_, index) => {
    return addDays(startDate, index);
  });
}

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

function findSavedDayTime(schedule: Pick<RoomSchedule, "days">, date: LocalDate): DayTimeRange | null {
  const day = schedule.days.find((item) => {
    return item.date === date;
  });

  return day === undefined || day.startTime === null || day.endTime === null ? null : { date, startTime: day.startTime, endTime: day.endTime };
}

/** 저장된 일정의 날마다 시작·종료 시각. 시각 조건이 없는 여행 날짜가 하나라도 있으면 `null`(조건을 다시 정해야 한다). */
export function getSavedDayTimes(schedule: Pick<RoomSchedule, "days">, dates: readonly LocalDate[]): DayTimeRange[] | null {
  const days = dates.map((date) => {
    return findSavedDayTime(schedule, date);
  });

  return days.every((day): day is DayTimeRange => {
    return day !== null;
  }) ? days : null;
}

/** W13 처음 값. 저장된 일정에 그날 시각 조건이 있으면 그 값을, 없으면 기본값을 쓴다. */
export function getInitialDayTimes(dates: readonly LocalDate[], saved: Pick<RoomSchedule, "days"> | null): DayTimeRange[] {
  return getDefaultDayTimes(dates).map((day) => {
    return (saved === null ? null : findSavedDayTime(saved, day.date)) ?? day;
  });
}

/**
 * 저장된 일정의 항목을 새 초안이 이어받게 한다(API-037 `fixedItems`).
 * 뺀 후보와 여행 날짜 밖의 항목은 서버가 거절하므로(C003·C012) 보내지 않고, 남은 항목의 순서를 1부터 다시 매긴다.
 */
function toFixedItems(schedule: Pick<RoomSchedule, "days">, roomPlaceIds: readonly number[], dates: readonly LocalDate[]): FixedItemDto[] {
  return schedule.days
    .filter((day) => {
      return dates.includes(day.date);
    })
    .flatMap((day) => {
      return day.items
        .filter((item) => {
          return roomPlaceIds.includes(item.roomPlaceId);
        })
        .map((item, index) => {
          return { roomPlaceId: item.roomPlaceId, visitDate: day.date, order: index + 1, stayMinutes: item.stayMinutes, fixed: item.fixed };
        });
    });
}

function toDayCondition(day: DayTimeRange): DayConditionDto {
  return { date: day.date, startTime: toServerTime(day.startTime), endTime: toServerTime(day.endTime) };
}

export type CreateDraftInput = {
  /** 요청에 실을 여행방 version. */
  baseVersion: number;
  /** 지금 빼지 않은 후보 전부. */
  roomPlaceIds: readonly number[];
  days: readonly DayTimeRange[];
  /** 이어받을 저장된 일정("일정 조정하기"). 새로 만들면 `null`. */
  keepSchedule: RoomSchedule | null;
};

/** API-037 요청. 저장된 일정을 이어받으면 그 항목과 출발·도착 지점을 함께 보낸다. */
export function toCreateDraftRequest({ baseVersion, roomPlaceIds, days, keepSchedule }: CreateDraftInput): CreateScheduleDraftRequestDto {
  const request: CreateScheduleDraftRequestDto = { baseVersion, roomPlaceIds: [...roomPlaceIds], days: days.map(toDayCondition) };

  if (keepSchedule === null) {
    return request;
  }

  const dates = days.map((day) => {
    return day.date;
  });

  return { ...request, startPoint: keepSchedule.startPoint, endPoint: keepSchedule.endPoint, fixedItems: toFixedItems(keepSchedule, roomPlaceIds, dates) };
}

// #endregion

// #region 초안 고치기(W14)

/** W14에서 고치는 항목. 시각과 이동 시간은 서버가 계산하므로 순서와 머무는 시간만 둔다. */
export type DraftLayoutItem = { itemId: number; roomPlaceId: number; stayMinutes: number; fixed: boolean };
export type DraftLayoutDay = DayTimeRange & { items: DraftLayoutItem[] };

/** 화면에서 고치는 초안의 배치. 서버에는 다시 계산(084)하거나 저장(039)할 때만 보낸다. */
export type DraftLayout = { days: DraftLayoutDay[]; unassigned: DraftLayoutItem[] };

/** 받은 초안을 고칠 수 있는 배치로. 미배치 항목은 머무는 시간을 받지 못해 서버 기본값으로 둔다. */
export function toDraftLayout(draft: Pick<ReadyScheduleDraft, "conditions" | "schedule">): DraftLayout {
  return {
    days: draft.conditions.map((condition) => {
      const day = draft.schedule.days.find((item) => {
        return item.date === condition.date;
      });

      return {
        ...condition,

        items: (day?.items ?? []).map((item) => {
          return { itemId: item.id, roomPlaceId: item.roomPlaceId, stayMinutes: item.stayMinutes, fixed: item.fixed };
        }),
      };
    }),

    unassigned: draft.schedule.unassigned.map((item) => {
      return { itemId: item.itemId, roomPlaceId: item.roomPlaceId, stayMinutes: DEFAULT_STAY_MINUTES, fixed: false };
    }),
  };
}

function isOtherItem(itemId: number) {
  return (item: DraftLayoutItem) => {
    return item.itemId !== itemId;
  };
}

function findLayoutItem(layout: DraftLayout, itemId: number): DraftLayoutItem | undefined {
  return [...layout.days.flatMap((day) => {
    return day.items;
  }), ...layout.unassigned].find((item) => {
    return item.itemId === itemId;
  });
}

/** 항목을 다른 날 끝으로 옮긴다(W14-M). 미배치 항목도 옮길 수 있다. 이미 그날에 있거나 없는 항목이면 그대로 돌려준다. */
export function moveItemToDay(layout: DraftLayout, itemId: number, toDate: LocalDate): DraftLayout {
  const item = findLayoutItem(layout, itemId);

  const target = layout.days.find((day) => {
    return day.date === toDate;
  });

  if (item === undefined || target === undefined || target.items.includes(item)) {
    return layout;
  }

  return {
    days: layout.days.map((day) => {
      const rest = day.items.filter(isOtherItem(itemId));
      return { ...day, items: day.date === toDate ? [...rest, item] : rest };
    }),

    unassigned: layout.unassigned.filter(isOtherItem(itemId)),
  };
}

/** 그날 안에서 항목을 한 칸 앞(-1)이나 뒤(1)로 옮긴다. 끝에서 더 갈 수 없으면 그대로 돌려준다. */
export function moveItemWithinDay(layout: DraftLayout, itemId: number, direction: -1 | 1): DraftLayout {
  return {
    ...layout,

    days: layout.days.map((day) => {
      const index = day.items.findIndex((item) => {
        return item.itemId === itemId;
      });

      const target = index + direction;

      if (index === -1 || target < 0 || target >= day.items.length) {
        return day;
      }

      const items = [...day.items];
      [items[index], items[target]] = [items[target], items[index]];
      return { ...day, items };
    }),
  };
}

export function clampStayMinutes(minutes: number): number {
  return Math.min(Math.max(Math.round(minutes), STAY_MIN_MINUTES), STAY_MAX_MINUTES);
}

/** 머무는 시간을 바꾼다(W14-T). 서버가 받는 범위를 벗어나면 범위 안으로 자른다. */
export function setItemStay(layout: DraftLayout, itemId: number, stayMinutes: number): DraftLayout {
  const minutes = clampStayMinutes(stayMinutes);

  return {
    ...layout,

    days: layout.days.map((day) => {
      return {
        ...day,

        items: day.items.map((item) => {
          return item.itemId === itemId ? { ...item, stayMinutes: minutes } : item;
        }),
      };
    }),
  };
}

/** 일정에서 빼 미배치로 둔다(W14-M 보조 동작). 후보 장소에는 그대로 남는다. */
export function unassignItem(layout: DraftLayout, itemId: number): DraftLayout {
  const day = layout.days.find((candidate) => {
    return candidate.items.some((item) => {
      return item.itemId === itemId;
    });
  });

  const item = day?.items.find((candidate) => {
    return candidate.itemId === itemId;
  });

  if (item === undefined) {
    return layout;
  }

  return {
    days: layout.days.map((candidate) => {
      return { ...candidate, items: candidate.items.filter(isOtherItem(itemId)) };
    }),

    unassigned: [...layout.unassigned, item],
  };
}

function isSameSequence(a: readonly number[], b: readonly number[]): boolean {
  return a.length === b.length && a.every((value, index) => {
    return value === b[index];
  });
}

function itemIdsOf(items: readonly DraftLayoutItem[]): number[] {
  return items.map((item) => {
    return item.itemId;
  });
}

/** 그날 항목의 순서와 머무는 시간이 같은지. 다르면 그날 시각과 이동 시간은 다시 계산해야 맞다. */
function isSameDayItems(a: readonly DraftLayoutItem[], b: readonly DraftLayoutItem[]): boolean {
  return isSameSequence(itemIdsOf(a), itemIdsOf(b)) && a.every((item, index) => {
    return item.stayMinutes === b[index].stayMinutes;
  });
}

/** 날마다 항목의 순서가 같은지. "순서 바꾸기"에서 바뀐 것이 있는지 볼 때 쓴다. */
export function isSameLayoutOrder(a: DraftLayout, b: DraftLayout): boolean {
  return a.days.length === b.days.length && a.days.every((day, index) => {
    return isSameSequence(itemIdsOf(day.items), itemIdsOf(b.days[index].items));
  });
}

function findLayoutDay(layout: DraftLayout, date: LocalDate): DraftLayoutDay | undefined {
  return layout.days.find((day) => {
    return day.date === date;
  });
}

/** 받은 초안과 달라진 날. 그날은 다시 계산하기 전까지 시각과 이동 시간을 보여 주지 않는다. */
export function getEditedDates(original: DraftLayout, edited: DraftLayout): LocalDate[] {
  return edited.days
    .filter((day) => {
      return !isSameDayItems(findLayoutDay(original, day.date)?.items ?? [], day.items);
    })
    .map((day) => {
      return day.date;
    });
}

/** 고친 뒤 아직 다시 계산하지 않은 날. 시각·이동 구간·경고는 낡아서 비운다. */
export function toUncalculatedDay(day: DraftLayoutDay): ScheduleDay {
  return {
    date: day.date,
    startTime: day.startTime,
    endTime: day.endTime,

    items: day.items.map((item) => {
      return { id: item.itemId, roomPlaceId: item.roomPlaceId, arrivalTime: null, departureTime: null, stayMinutes: item.stayMinutes, fixed: item.fixed };
    }),

    legs: [],
    warnings: [],
  };
}

type DraftVersions = Pick<ReadyScheduleDraft, "baseVersion" | "draftVersion">;

/**
 * API-084 "다시 계산" 요청. 받은 초안에서 달라진 날의 항목 전부와, 일정에서 뺀 항목만 보낸다.
 * 서버는 보낸 항목과 그대로 둔 항목의 순서가 겹치면 거절하므로(SC005) 달라진 날은 모든 항목의 순서를 함께 보낸다.
 */
export function toAdjustDraftRequest(draft: DraftVersions, original: DraftLayout, edited: DraftLayout): AdjustScheduleDraftRequestDto {
  const editedDates = getEditedDates(original, edited);
  const originalUnassigned = itemIdsOf(original.unassigned);

  return {
    baseVersion: draft.baseVersion,
    draftVersion: draft.draftVersion,

    items: edited.days
      .filter((day) => {
        return editedDates.includes(day.date);
      })
      .flatMap((day) => {
        return day.items.map((item, index) => {
          return { itemId: item.itemId, roomPlaceId: item.roomPlaceId, visitDate: day.date, order: index + 1, stayMinutes: item.stayMinutes };
        });
      }),

    removeItemIds: itemIdsOf(edited.unassigned).filter((itemId) => {
      return !originalUnassigned.includes(itemId);
    }),
  };
}

type SavableDraft = DraftVersions & Pick<ReadyScheduleDraft, "startPoint" | "endPoint">;

/** API-039 저장 요청. 고친 배치 전체를 보낸다. 지점을 고치는 화면이 없어 초안의 지점을 그대로 돌려보낸다. */
export function toSaveScheduleRequest(draftKey: string, draft: SavableDraft, layout: DraftLayout): SaveScheduleRequestDto {
  return {
    draftKey,
    draftVersion: draft.draftVersion,
    baseVersion: draft.baseVersion,

    days: layout.days.map((day) => {
      return {
        ...toDayCondition(day),

        items: day.items.map((item, index) => {
          return { itemId: item.itemId, roomPlaceId: item.roomPlaceId, order: index + 1, stayMinutes: item.stayMinutes, fixed: item.fixed };
        }),
      };
    }),

    unassignedItemIds: itemIdsOf(layout.unassigned),
    startPoint: draft.startPoint,
    endPoint: draft.endPoint,
  };
}

/** 초안을 저장하면 지금 일정에서 달라지는 점. */
export type ScheduleChanges = {
  /** 넣은 장소나 순서가 바뀌는 날(초안 기준). */
  changedDates: LocalDate[];
  /** 지금 일정에 있는데 초안 일정에는 없는 후보(roomPlaceId). */
  droppedPlaceIds: number[];
};

function placeIdsOf(items: readonly { roomPlaceId: number }[]): number[] {
  return items.map((item) => {
    return item.roomPlaceId;
  });
}

/** 지금 일정과 고친 초안을 날짜별로 비교한다(ScheduleTimeline 체크리스트 "현재 일정과 달라지는 점"). */
export function getScheduleChanges(current: Pick<RoomSchedule, "days">, layout: DraftLayout): ScheduleChanges {
  const currentOrders = new Map(current.days.map((day) => {
    return [day.date, placeIdsOf(day.items)];
  }));

  const draftPlaceIds = new Set(layout.days.flatMap((day) => {
    return placeIdsOf(day.items);
  }));

  const currentPlaceIds = new Set(current.days.flatMap((day) => {
    return placeIdsOf(day.items);
  }));

  return {
    changedDates: layout.days.filter((day) => {
      return !isSameSequence(currentOrders.get(day.date) ?? [], placeIdsOf(day.items));
    }).map((day) => {
      return day.date;
    }),

    droppedPlaceIds: [...currentPlaceIds].filter((roomPlaceId) => {
      return !draftPlaceIds.has(roomPlaceId);
    }),
  };
}

// #endregion
