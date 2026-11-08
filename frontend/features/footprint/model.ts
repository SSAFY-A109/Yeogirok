import { assertId, ContractMismatchError, toKnownEnum } from "../../lib/contract";
import { localDateParts, makeLocalDate, parseServerDateTime, type LocalDate } from "../../lib/date";

export const GUESTBOOK_LIMIT = 200;

export type FootprintFilters = { bbox?: string; from?: LocalDate; to?: LocalDate; category?: string };
export type FootprintListParams = FootprintFilters & { cursor?: string; limit?: number };
export type FootprintDto = {
  id: number;
  latitude: number;
  longitude: number;
  locationName: string | null;
  address: string | null;
  body: string | null;
  categoryCodes: string[];
  visitedAt: string;
};
export type FootprintListDto = { items: FootprintDto[]; nextCursor: string | null };
export type FootprintDetailDto = FootprintDto & { createdAt: string; version: number };
export type UpdateGuestbookDto = { version: number; body: string };
export type GuestbookUpdateDto = { id: number; body: string | null; updatedAt: string; version: number };
export type FootprintDeleteDto = { deleted: true; statisticsStatus: string };

export function normalizeGuestbookBody(body: string) {
  return body.replace(/\r\n?/g, "\n");
}

export function guestbookLength(body: string) {
  return Array.from(normalizeGuestbookBody(body)).length;
}

const VISIT_DATE_FORMAT = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "long", day: "numeric", weekday: "short", hour: "2-digit", minute: "2-digit", hour12: false });

export function formatFootprintDate(date: Date) {
  return VISIT_DATE_FORMAT.format(date);
}

/** 목록의 from/to는 서버와 동일하게 한국 날짜로 계산한다. */
export function footprintPeriod(period: "recent" | "month" | "all", now = new Date()): FootprintFilters {
  if (period === "all") {
    return {};
  }

  const to = new Date(now.getTime() + 9 * 3_600_000).toISOString().slice(0, 10);
  const { year, month, day } = localDateParts(to);
  const previousMonth = month - (period === "month" ? 1 : 3);
  const first = new Date(Date.UTC(year, previousMonth - 1, 1));
  const lastDay = new Date(Date.UTC(year, previousMonth, 0)).getUTCDate();
  return { from: makeLocalDate(first.getUTCFullYear(), first.getUTCMonth() + 1, Math.min(day, lastDay)), to };
}

function coordinate(value: unknown, max: number, field: string) {
  if (typeof value !== "number" || !Number.isFinite(value) || Math.abs(value) > max) {
    throw new ContractMismatchError(field);
  }

  return value;
}

function nullableText(value: unknown, field: string) {
  if (value !== null && typeof value !== "string") {
    throw new ContractMismatchError(field);
  }

  return value;
}

function toFootprint(dto: FootprintDto) {
  if (!Array.isArray(dto.categoryCodes) || dto.categoryCodes.some((code) => {
    return typeof code !== "string";
  })) {
    throw new ContractMismatchError("categoryCodes");
  }

  return {
    id: assertId(dto.id, "id"),
    lat: coordinate(dto.latitude, 90, "latitude"),
    lng: coordinate(dto.longitude, 180, "longitude"),
    place: nullableText(dto.locationName, "locationName"),
    address: nullableText(dto.address, "address"),
    body: nullableText(dto.body, "body"),
    categoryCodes: dto.categoryCodes,
    visitedAt: parseServerDateTime(dto.visitedAt, "visitedAt"),
  };
}

export type Footprint = ReturnType<typeof toFootprint>;

export function toFootprintList(dto: FootprintListDto) {
  return { items: dto.items.map(toFootprint), nextCursor: nullableText(dto.nextCursor, "nextCursor") };
}

export function toFootprintDetail(dto: FootprintDetailDto) {
  return { ...toFootprint(dto), createdAt: parseServerDateTime(dto.createdAt, "createdAt"), version: assertId(dto.version, "version") };
}

export type FootprintDetail = ReturnType<typeof toFootprintDetail>;

export function toGuestbookUpdate(dto: GuestbookUpdateDto) {
  return {
    id: assertId(dto.id, "id"),
    body: nullableText(dto.body, "body"),
    updatedAt: parseServerDateTime(dto.updatedAt, "updatedAt"),
    version: assertId(dto.version, "version"),
  };
}

export function toFootprintDelete(dto: FootprintDeleteDto) {
  if (dto.deleted !== true) {
    throw new ContractMismatchError("deleted");
  }

  return { deleted: true, statisticsStatus: toKnownEnum(dto.statisticsStatus, ["PENDING", "APPLIED", "UNCHANGED"] as const) };
}
