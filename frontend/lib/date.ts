import { ContractMismatchError } from "./contract";

/** 계약의 date 형식(`2026-11-08`). 시간대를 붙이지 않는 달력 날짜다. */
export type LocalDate = string;

const LOCAL_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const SERVER_DATE_TIME_PATTERN = /^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})([+-]\d{2})(?::?(\d{2}))?$/;
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"] as const;
const DAY_MS = 86_400_000;

type DateParts = { year: number; month: number; day: number };

function toParts(value: LocalDate): DateParts {
  const match = LOCAL_DATE_PATTERN.exec(value);

  if (!match) {
    throw new ContractMismatchError(`date(${value})`);
  }

  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) };
}

function toUtcTime(value: LocalDate): number {
  const { year, month, day } = toParts(value);
  return Date.UTC(year, month - 1, day);
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

/** 연·월·일 숫자로 `LocalDate`를 만든다. 월은 1부터 센다. */
export function makeLocalDate(year: number, month: number, day: number): LocalDate {
  return `${year}-${pad(month)}-${pad(day)}`;
}

/** 계약의 date 문자열을 검증한다. 형식이 다르거나 없는 날짜면 `ContractMismatchError`. */
export function parseLocalDate(value: unknown, field: string): LocalDate {
  if (typeof value !== "string") {
    throw new ContractMismatchError(field);
  }

  const match = LOCAL_DATE_PATTERN.exec(value);

  if (!match) {
    throw new ContractMismatchError(field);
  }

  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));

  if (date.getUTCDate() !== Number(match[3])) {
    throw new ContractMismatchError(field);
  }

  return value;
}

/** `null`을 허용하는 date 필드를 검증한다. */
export function parseNullableLocalDate(value: unknown, field: string): LocalDate | null {
  return value === null ? null : parseLocalDate(value, field);
}

/**
 * 계약의 datetime(`2026-07-27 15:30:00+09`)을 `Date`로 바꾼다.
 * 날짜와 시각 사이가 공백이고 오프셋을 포함하므로 ISO 파서에 그대로 넣지 않는다.
 */
export function parseServerDateTime(value: unknown, field: string): Date {
  if (typeof value !== "string") {
    throw new ContractMismatchError(field);
  }

  const match = SERVER_DATE_TIME_PATTERN.exec(value);

  if (!match) {
    throw new ContractMismatchError(field);
  }

  const date = new Date(`${match[1]}T${match[2]}${match[3]}:${match[4] ?? "00"}`);

  if (Number.isNaN(date.getTime())) {
    throw new ContractMismatchError(field);
  }

  return date;
}

/** 서버 시간대(Asia/Seoul)의 UTC 오프셋(시간). 서머타임이 없어 고정이다. */
const SERVER_UTC_OFFSET_HOURS = 9;
const HOUR_MS = 3_600_000;

/** `Date`를 계약의 datetime(`2026-07-27 15:30:00+09`)으로 적는다. 기기 시간대와 상관없이 서버 시간대로 적는다. */
export function formatServerDateTime(date: Date): string {
  const seoul = new Date(date.getTime() + SERVER_UTC_OFFSET_HOURS * HOUR_MS);
  const day = `${seoul.getUTCFullYear()}-${pad(seoul.getUTCMonth() + 1)}-${pad(seoul.getUTCDate())}`;
  const time = `${pad(seoul.getUTCHours())}:${pad(seoul.getUTCMinutes())}:${pad(seoul.getUTCSeconds())}`;
  return `${day} ${time}+${pad(SERVER_UTC_OFFSET_HOURS)}`;
}

/** 기기 시간대 기준의 시각(`15:07`). */
export function formatTimeOfDay(date: Date): string {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 기기 시간대 기준의 오늘 날짜. */
export function todayLocalDate(now: Date = new Date()): LocalDate {
  return makeLocalDate(now.getFullYear(), now.getMonth() + 1, now.getDate());
}

/** `Date`를 기기 시간대 기준의 달력 날짜로 바꾼다. */
export function toLocalDate(date: Date): LocalDate {
  return todayLocalDate(date);
}

/** a보다 b가 며칠 뒤인지 센다. 같은 날이면 0. */
export function diffDays(a: LocalDate, b: LocalDate): number {
  return Math.round((toUtcTime(b) - toUtcTime(a)) / DAY_MS);
}

/** 두 날짜를 비교한다. a가 앞이면 음수, 같으면 0, 뒤면 양수. */
export function compareLocalDate(a: LocalDate, b: LocalDate): number {
  return diffDays(b, a);
}

/** 날짜에 일수를 더한다. */
export function addDays(value: LocalDate, days: number): LocalDate {
  const date = new Date(toUtcTime(value) + days * DAY_MS);
  return makeLocalDate(date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate());
}

/** 요일 이름(일~토). */
export function weekdayOf(value: LocalDate): (typeof WEEKDAYS)[number] {
  return WEEKDAYS[new Date(toUtcTime(value)).getUTCDay()];
}

/** 연·월·일 숫자. 월은 1부터 센다. */
export function localDateParts(value: LocalDate): DateParts {
  return toParts(value);
}

type MonthDayOptions = { weekday?: boolean; month?: boolean };

/** `11월 8일 (일)` 형식. `month: false`면 `8일 (일)`. */
export function formatMonthDay(value: LocalDate, { weekday = true, month = true }: MonthDayOptions = {}): string {
  const parts = toParts(value);
  const monthText = month ? `${parts.month}월 ` : "";
  const weekdayText = weekday ? ` (${weekdayOf(value)})` : "";
  return `${monthText}${parts.day}일${weekdayText}`;
}

/** `11/8 (일)` 형식. `parens: false`면 `11/8 일`(일차 칩처럼 좁은 자리). */
export function formatShortMonthDay(value: LocalDate, { parens = true }: { parens?: boolean } = {}): string {
  const parts = toParts(value);
  const weekday = weekdayOf(value);
  return parens ? `${parts.month}/${parts.day} (${weekday})` : `${parts.month}/${parts.day} ${weekday}`;
}

type DateRangeOptions = {
  /** 요일을 붙인다. 기본값 true. */
  weekday?: boolean;
  /** 같은 달이어도 끝 날짜에 월을 다시 쓴다. 기본값 false(`11월 8일 (일) – 9일 (월)`). */
  repeatMonth?: boolean;
};

/**
 * 여행 기간 문구. 같은 날이면 하루만, 같은 달이면 끝 날짜의 월을 생략한다.
 * @example formatDateRange("2026-11-08", "2026-11-09") // "11월 8일 (일) – 9일 (월)"
 */
export function formatDateRange(start: LocalDate, end: LocalDate, { weekday = true, repeatMonth = false }: DateRangeOptions = {}): string {
  const startText = formatMonthDay(start, { weekday });

  if (start === end) {
    return startText;
  }

  const sameMonth = toParts(start).year === toParts(end).year && toParts(start).month === toParts(end).month;
  const endText = formatMonthDay(end, { weekday, month: repeatMonth || !sameMonth });
  return `${startText} – ${endText}`;
}

/** `1박 2일`, 같은 날이면 `당일치기`. */
export function formatTripLength(start: LocalDate, end: LocalDate): string {
  const nights = diffDays(start, end);
  return nights <= 0 ? "당일치기" : `${nights}박 ${nights + 1}일`;
}

/** 출발일까지 남은 날. 오늘이면 `D-DAY`, 이미 지났으면 `null`. */
export function formatDDay(start: LocalDate, today: LocalDate = todayLocalDate()): string | null {
  const days = diffDays(today, start);

  if (days < 0) {
    return null;
  }

  return days === 0 ? "D-DAY" : `D-${days}`;
}

/**
 * 달력 한 달의 칸. 일요일부터 시작하는 주 단위 배열이며, 앞뒤 빈칸은 `null`이다.
 * @param month 1부터 센다.
 */
export function getMonthGrid(year: number, month: number): (LocalDate | null)[][] {
  const firstWeekday = new Date(Date.UTC(year, month - 1, 1)).getUTCDay();
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();

  const cells: (LocalDate | null)[] = Array.from({ length: firstWeekday }, () => {
    return null;
  });

  for (let day = 1; day <= daysInMonth; day += 1) {
    cells.push(makeLocalDate(year, month, day));
  }

  while (cells.length % 7 !== 0) {
    cells.push(null);
  }

  const weeks: (LocalDate | null)[][] = [];

  for (let index = 0; index < cells.length; index += 7) {
    weeks.push(cells.slice(index, index + 7));
  }

  return weeks;
}
