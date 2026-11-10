import { delay, http } from "msw";

import { IDEMPOTENCY_HEADER } from "../../idempotency";
import type {
  AdjustScheduleDraftRequestDto,
  CreateScheduleDraftRequestDto,
  DayConditionDto,
  DraftJobResponseDto,
  SaveScheduleRequestDto,
  ScheduleResponseDto,
} from "../../../features/schedule/model";

import { mockDb } from "../db";
import { getActivePlaces, MOCK_ME, type MockRoom } from "../fixtures/room";
import {
  calculateMockDay,
  MOCK_DEFAULT_STAY_MINUTES,
  planMockDraftItems,
  settleMockDraft,
  toMockDraftResponse,
  toMockScheduleView,
  trimMockOverflow,
  type MockCalculatedDay,
  type MockDraftItem,
  type MockScheduleDraft,
} from "../fixtures/schedule";
import { fail, MOCK_DELAY_MS, ok, readId } from "./respond";

// 일정 목(API-036~039·084). 서버 검증의 핵심만 흉내 낸다(backend/dev schedule draft 서비스, 2026-11-10).
// 직접 편집(API-040)과 작업 상태(API-011)는 앱이 부르지 않아 핸들러를 두지 않는다.

/** 처음 만드는 초안은 처리 중 화면을 볼 수 있게 오래, "다시 계산"은 짧게 걸린다. */
const NEW_DRAFT_MS = 3_000;
const RECALCULATION_MS = 800;
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$/;
const MIN_PLACES = 3;
const MAX_STAY_MINUTES = 720;

type Rejection = { status: number; code: string; message: string };

function reject({ status, code, message }: Rejection) {
  return fail(status, code, message);
}

function notAllowed(message: string): Rejection {
  return { status: 400, code: "C003", message };
}

function dateRange(message: string): Rejection {
  return { status: 400, code: "C012", message };
}

const VERSION_MISMATCH: Rejection = { status: 409, code: "C004", message: "리소스 버전이 일치하지 않습니다. 최신 정보를 다시 조회해 주세요." };
const DRAFT_NOT_FOUND: Rejection = { status: 404, code: "SC001", message: "요청한 일정 초안이 해당 여행방에 없습니다." };
const DRAFT_IN_PROGRESS: Rejection = { status: 422, code: "SC003", message: "일정 초안을 아직 계산하고 있습니다." };
const DRAFT_FAILED: Rejection = { status: 422, code: "SC004", message: "생성에 실패한 일정 초안입니다." };

/** 경로의 여행방을 찾는다. 없으면 404 RO001, 내가 참여자가 아니면 403 RO002다. */
function findMemberRoom(roomId: string | readonly string[] | undefined): MockRoom | Rejection {
  const id = readId(roomId);
  const room = id === null ? undefined : mockDb.rooms.get(id);

  if (room === undefined) {
    return { status: 404, code: "RO001", message: "여행방을 찾을 수 없습니다." };
  }

  const isMember = room.members.some((member) => {
    return member.userId === MOCK_ME.userId;
  });

  return isMember ? room : { status: 403, code: "RO002", message: "여행방 참여자가 아닙니다." };
}

function isRejection(value: object): value is Rejection {
  return "code" in value && "status" in value;
}

function nextSeq(): number {
  const seq = mockDb.nextScheduleSeq;
  mockDb.nextScheduleSeq += 1;
  return seq;
}

function tripDatesOf(room: MockRoom): string[] {
  if (room.startDate === null || room.endDate === null) {
    return [];
  }

  const dates: string[] = [];
  const cursor = new Date(`${room.startDate}T00:00:00Z`);
  const end = new Date(`${room.endDate}T00:00:00Z`);

  while (cursor <= end) {
    dates.push(cursor.toISOString().slice(0, 10));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }

  return dates;
}

/** 여행의 모든 날짜를 중복 없이 담고 날마다 시작 < 종료여야 한다(서버 `DraftRules.checkDays`). */
function checkDays(room: MockRoom, days: readonly DayConditionDto[]): Rejection | null {
  const tripDates = tripDatesOf(room);

  if (tripDates.length === 0) {
    return notAllowed("여행 날짜가 정해지지 않은 방입니다.");
  }

  const seen = new Set<string>();

  for (const day of days) {
    if (!TIME_PATTERN.test(day.startTime) || !TIME_PATTERN.test(day.endTime)) {
      return { status: 400, code: "C002", message: "days: 시각 형식은 HH:mm:ss 입니다." };
    }

    if (!tripDates.includes(day.date) || seen.has(day.date) || day.startTime >= day.endTime) {
      return dateRange(`days: 날짜나 시작·종료 시각이 올바르지 않습니다(${day.date}).`);
    }

    seen.add(day.date);
  }

  return seen.size === tripDates.length ? null : dateRange("days: 여행의 모든 날짜가 있어야 합니다.");
}

/** 1분 단위 1~720분. 음수 SC006, 0·720 초과 C003(서버 `DraftRules.checkStay`). */
function checkStay(minutes: number, field: string): Rejection | null {
  if (!Number.isInteger(minutes)) {
    return { status: 400, code: "C002", message: `${field}: 정수여야 합니다.` };
  }

  if (minutes < 0) {
    return { status: 400, code: "SC006", message: `${field}: 음수일 수 없습니다.` };
  }

  return minutes === 0 || minutes > MAX_STAY_MINUTES ? notAllowed(`${field}: 1~${MAX_STAY_MINUTES}분이어야 합니다.`) : null;
}

function sortedDays(days: readonly DayConditionDto[]): DayConditionDto[] {
  return days.map(({ date, startTime, endTime }) => {
    return { date, startTime, endTime };
  }).sort((a, b) => {
    return a.date.localeCompare(b.date);
  });
}

function toJobResponse(draft: MockScheduleDraft): DraftJobResponseDto {
  return { jobId: draft.jobId, draftKey: draft.draftKey, status: "QUEUED" };
}

// #region API-037 초안 만들기

/** 요청을 확인하고 계산 전 항목을 만든다. 이어받은 항목을 요청 순서대로 먼저(itemId 1부터), 나머지 후보를 `roomPlaceIds` 순서로 만든다. */
function prepareDraftItems(room: MockRoom, body: CreateScheduleDraftRequestDto): MockDraftItem[] | Rejection {
  const invalidDays = checkDays(room, body.days);

  if (invalidDays !== null) {
    return invalidDays;
  }

  const activeIds = getActivePlaces(room).map((place) => {
    return place.id;
  });

  const requested = new Set(body.roomPlaceIds);

  const sameAsActive = requested.size === body.roomPlaceIds.length && requested.size === activeIds.length && activeIds.every((id) => {
    return requested.has(id);
  });

  if (!sameAsActive) {
    return notAllowed("roomPlaceIds: 현재 담은 후보 전부를 보내야 합니다.");
  }

  if (requested.size < MIN_PLACES) {
    return notAllowed(`roomPlaceIds: 후보가 ${MIN_PLACES}곳 이상이어야 합니다.`);
  }

  const stayMinutes = body.stayMinutes ?? {};

  for (const [key, minutes] of Object.entries(stayMinutes)) {
    const invalid = requested.has(Number(key)) ? checkStay(minutes, `stayMinutes.${key}`) : notAllowed(`stayMinutes: roomPlaceIds 에 없는 후보입니다(${key}).`);

    if (invalid !== null) {
      return invalid;
    }
  }

  const dates = body.days.map((day) => {
    return day.date;
  });

  const fixedItems = body.fixedItems ?? [];
  const fixedSlots = new Set<string>();

  for (const [index, item] of fixedItems.entries()) {
    const path = `fixedItems[${index}]`;

    if (!requested.has(item.roomPlaceId) || item.order < 1) {
      return notAllowed(`${path}: 후보나 순서가 올바르지 않습니다.`);
    }

    if (!dates.includes(item.visitDate)) {
      return dateRange(`${path}.visitDate: 여행 날짜 밖입니다.`);
    }

    const invalidStay = checkStay(item.stayMinutes, `${path}.stayMinutes`);

    if (invalidStay !== null) {
      return invalidStay;
    }

    const slot = `${item.visitDate}#${item.order}`;

    if (item.fixed && fixedSlots.has(slot)) {
      return notAllowed(`${path}: 같은 날짜·순서에 고정한 항목이 둘 있습니다.`);
    }

    fixedSlots.add(slot);
  }

  const inherited = fixedItems.map((item, index) => {
    return { itemId: index + 1, roomPlaceId: item.roomPlaceId, visitDate: item.visitDate, order: item.order, stayMinutes: item.stayMinutes, fixed: item.fixed };
  });

  const inheritedIds = new Set(inherited.map((item) => {
    return item.roomPlaceId;
  }));

  const rest = body.roomPlaceIds.filter((id) => {
    return !inheritedIds.has(id);
  }).map((roomPlaceId, index) => {
    return {
      itemId: inherited.length + index + 1,
      roomPlaceId,
      visitDate: null,
      order: null,
      stayMinutes: stayMinutes[String(roomPlaceId)] ?? MOCK_DEFAULT_STAY_MINUTES,
      fixed: false,
    };
  });

  return [...inherited, ...rest];
}

// #endregion

// #region API-084 다시 계산

/** 요청을 반영한 새 배치. 날짜마다 순서를 1부터 다시 매기고, 보낸 항목과 그대로 둔 항목의 순서가 겹치면 SC005다(서버 `rearrange`). */
function rearrangeDraftItems(draft: MockScheduleDraft, body: AdjustScheduleDraftRequestDto): MockDraftItem[] | Rejection {
  const dates = draft.days.map((day) => {
    return day.date;
  });

  const removed = new Set(body.removeItemIds);
  const edits = new Map<number, AdjustScheduleDraftRequestDto["items"][number]>();

  for (const itemId of removed) {
    if (!draft.items.some((item) => {
      return item.itemId === itemId;
    })) {
      return notAllowed(`removeItemIds: 이 초안의 항목이 아닙니다(${itemId}).`);
    }
  }

  for (const edit of body.items) {
    const path = `items[itemId=${edit.itemId}]`;

    const original = draft.items.find((item) => {
      return item.itemId === edit.itemId;
    });

    if (original === undefined || original.roomPlaceId !== edit.roomPlaceId || removed.has(edit.itemId) || edits.has(edit.itemId) || edit.order < 1) {
      return notAllowed(`${path}: 이 초안의 항목이 아니거나 두 번 보냈거나 순서가 올바르지 않습니다.`);
    }

    if (!dates.includes(edit.visitDate)) {
      return dateRange(`${path}.visitDate: 여행 날짜 밖입니다.`);
    }

    const invalidStay = checkStay(edit.stayMinutes, `${path}.stayMinutes`);

    if (invalidStay !== null) {
      return invalidStay;
    }

    edits.set(edit.itemId, edit);
  }

  const next = draft.items.map((item) => {
    const edit = edits.get(item.itemId);

    if (removed.has(item.itemId)) {
      return { ...item, visitDate: null, order: null };
    }

    return edit === undefined ? item : { ...item, visitDate: edit.visitDate, order: edit.order, stayMinutes: edit.stayMinutes };
  });

  const slots = new Set<string>();

  for (const item of next) {
    const slot = `${item.visitDate}#${item.order}`;

    if (item.visitDate !== null && slots.has(slot)) {
      return { status: 400, code: "SC005", message: `${item.visitDate} 의 방문 순서 ${item.order} 가 중복됩니다.` };
    }

    slots.add(slot);
  }

  return next.map((item) => {
    if (item.visitDate === null) {
      return item;
    }

    const before = next.filter((other) => {
      return other.visitDate === item.visitDate && (other.order ?? 0) < (item.order ?? 0);
    });

    return { ...item, order: before.length + 1 };
  });
}

// #endregion

// #region API-039 저장

type SavePlan = { days: MockCalculatedDay[]; unassignedRoomPlaceIds: number[] };

/** 초안의 모든 항목이 `days.items`나 `unassignedItemIds` 중 정확히 한 곳에 있어야 하고, 날마다 순서는 1부터 빈 번호 없이 이어져야 한다. */
function planSave(room: MockRoom, draft: MockScheduleDraft, body: SaveScheduleRequestDto): SavePlan | Rejection {
  const invalidDays = checkDays(room, body.days);

  if (invalidDays !== null) {
    return invalidDays;
  }

  const activeIds = new Set(getActivePlaces(room).map((place) => {
    return place.id;
  }));

  const seen = new Set<number>();

  const checkItem = (itemId: number, roomPlaceId: number | undefined): Rejection | null => {
    const original = draft.items.find((item) => {
      return item.itemId === itemId;
    });

    if (original === undefined || (roomPlaceId !== undefined && original.roomPlaceId !== roomPlaceId) || seen.has(itemId) || !activeIds.has(original.roomPlaceId)) {
      return notAllowed(`itemId ${itemId}: 원본 초안의 항목이 아니거나 두 번 있거나 현재 후보가 아닙니다.`);
    }

    seen.add(itemId);
    return null;
  };

  const days: SavePlan["days"] = [];

  for (const day of [...body.days].sort((a, b) => {
    return a.date.localeCompare(b.date);
  })) {
    const orders = new Set<number>();

    for (const item of day.items) {
      const invalid = checkItem(item.itemId, item.roomPlaceId) ?? checkStay(item.stayMinutes, `itemId ${item.itemId}.stayMinutes`);

      if (invalid !== null) {
        return invalid;
      }

      if (orders.has(item.order)) {
        return { status: 400, code: "SC005", message: `${day.date} 의 방문 순서 ${item.order} 가 중복됩니다.` };
      }

      orders.add(item.order);
    }

    for (let order = 1; order <= orders.size; order += 1) {
      if (!orders.has(order)) {
        return notAllowed(`${day.date}: 방문 순서는 1부터 빈 번호 없이 이어져야 합니다.`);
      }
    }

    const items = [...day.items].sort((a, b) => {
      return a.order - b.order;
    }).map((item) => {
      return { id: nextSeq(), roomPlaceId: item.roomPlaceId, stayMinutes: item.stayMinutes, fixed: item.fixed };
    });

    const calculated = calculateMockDay({ date: day.date, startTime: day.startTime, endTime: day.endTime, items }, room.places, room.transportMode);

    if (calculated.exceedsEndTime) {
      return { status: 422, code: "SC002", message: `${day.date}: 마지막 일정이 종료 시각을 넘깁니다.` };
    }

    days.push(calculated);
  }

  const unassignedRoomPlaceIds: number[] = [];

  for (const itemId of body.unassignedItemIds) {
    const invalid = checkItem(itemId, undefined);

    const original = draft.items.find((item) => {
      return item.itemId === itemId;
    });

    if (invalid !== null || original === undefined) {
      return invalid ?? notAllowed(`unassignedItemIds: 원본 초안의 항목이 아닙니다(${itemId}).`);
    }

    unassignedRoomPlaceIds.push(original.roomPlaceId);
  }

  return seen.size === draft.items.length ? { days, unassignedRoomPlaceIds } : notAllowed("초안의 모든 항목이 days.items 나 unassignedItemIds 중 한 곳에 있어야 합니다.");
}

// #endregion

/** 초안을 찾고 계산 시간이 지났으면 끝난 상태로 바꾼다. 다른 여행방의 초안이면 없는 것으로 본다. */
function findDraft(room: MockRoom, draftKey: string | readonly string[] | undefined): MockScheduleDraft | undefined {
  const draft = typeof draftKey === "string" ? mockDb.scheduleDrafts.get(draftKey) : undefined;

  if (draft === undefined || draft.roomId !== room.id) {
    return undefined;
  }

  settleMockDraft(draft);
  return draft;
}

/** 초안이 다시 계산·저장할 수 있는 상태이고 버전이 맞는지 확인한다. */
function checkDraftUsable(room: MockRoom, draft: MockScheduleDraft, versions: { baseVersion: number; draftVersion: number }): Rejection | null {
  if (draft.pending !== null) {
    return DRAFT_IN_PROGRESS;
  }

  if (draft.error !== null) {
    return DRAFT_FAILED;
  }

  const sameVersions = draft.draftVersion === versions.draftVersion && draft.baseVersion === versions.baseVersion && room.version === versions.baseVersion;
  return sameVersions ? null : VERSION_MISMATCH;
}

export const scheduleHandlers = [
  // API-036 확정 일정. 확정 항목이 없으면 `schedule`이 `null`이다.
  http.get("*/api/v1/rooms/:roomId/schedule", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const room = findMemberRoom(params.roomId);

    if (isRejection(room)) {
      return reject(room);
    }

    const response: ScheduleResponseDto = { schedule: room.schedule, version: room.version };
    return ok(response);
  }),

  // API-037 초안 만들기(작업, 202). 같은 멱등키·같은 내용이면 처음 만든 작업을, 다른 내용이면 C005를 돌려준다.
  http.post("*/api/v1/rooms/:roomId/schedule-drafts", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const idempotencyKey = request.headers.get(IDEMPOTENCY_HEADER)?.trim();

    if (!idempotencyKey) {
      return fail(400, "C001", "Idempotency-Key 헤더가 필요합니다.");
    }

    const room = findMemberRoom(params.roomId);

    if (isRejection(room)) {
      return reject(room);
    }

    const requestBody = await request.text();
    const body = JSON.parse(requestBody) as CreateScheduleDraftRequestDto;

    if (typeof body.baseVersion !== "number" || !Array.isArray(body.roomPlaceIds) || !Array.isArray(body.days)) {
      return fail(400, "C001", "baseVersion·roomPlaceIds·days는 필수입니다.");
    }

    const existing = [...mockDb.scheduleDrafts.values()].find((draft) => {
      return draft.roomId === room.id && draft.idempotencyKey === idempotencyKey;
    });

    if (existing !== undefined) {
      return existing.requestBody === requestBody ? ok(toJobResponse(existing), 202) : fail(409, "C005", "같은 멱등키로 다른 내용의 요청을 보냈습니다.");
    }

    if (body.baseVersion !== room.version) {
      return reject(VERSION_MISMATCH);
    }

    const items = prepareDraftItems(room, body);

    if (isRejection(items)) {
      return reject(items);
    }

    const days = sortedDays(body.days);
    const seq = nextSeq();

    const draft: MockScheduleDraft = {
      roomId: room.id,
      draftKey: `mock-draft-${seq}`,
      jobId: `mock-job-${seq}`,
      idempotencyKey,
      requestBody,
      baseVersion: room.version,
      draftVersion: 0,
      transportMode: room.transportMode,
      days,
      items,
      pending: { readyAt: Date.now() + NEW_DRAFT_MS, ...planMockDraftItems(items, days, room.places, room.transportMode) },
      error: null,
      adjustRequests: new Map(),
      applied: null,
    };

    mockDb.scheduleDrafts.set(draft.draftKey, draft);
    return ok(toJobResponse(draft), 202);
  }),

  // API-038 초안 조회. 계산 시간 전에는 RUNNING으로 보인다.
  http.get("*/api/v1/rooms/:roomId/schedule-drafts/:draftKey", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const room = findMemberRoom(params.roomId);

    if (isRejection(room)) {
      return reject(room);
    }

    const draft = findDraft(room, params.draftKey);
    return draft === undefined ? reject(DRAFT_NOT_FOUND) : ok(toMockDraftResponse(draft, room.places));
  }),

  // API-084 다시 계산(작업, 202). 끝나면 draftVersion이 1 오르고, 실패하면 마지막 성공 내용 그대로다.
  http.patch("*/api/v1/rooms/:roomId/schedule-drafts/:draftKey", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const idempotencyKey = request.headers.get(IDEMPOTENCY_HEADER)?.trim();

    if (!idempotencyKey) {
      return fail(400, "C001", "Idempotency-Key 헤더가 필요합니다.");
    }

    const room = findMemberRoom(params.roomId);

    if (isRejection(room)) {
      return reject(room);
    }

    const draft = findDraft(room, params.draftKey);

    if (draft === undefined) {
      return reject(DRAFT_NOT_FOUND);
    }

    const requestBody = await request.text();
    const body = JSON.parse(requestBody) as AdjustScheduleDraftRequestDto;

    if (typeof body.baseVersion !== "number" || typeof body.draftVersion !== "number" || !Array.isArray(body.items)) {
      return fail(400, "C001", "baseVersion·draftVersion·items는 필수입니다.");
    }

    const previous = draft.adjustRequests.get(idempotencyKey);

    if (previous !== undefined) {
      return previous.requestBody === requestBody
        ? ok({ jobId: previous.jobId, draftKey: draft.draftKey, status: "QUEUED" } satisfies DraftJobResponseDto, 202)
        : fail(409, "C005", "같은 멱등키로 다른 내용의 요청을 보냈습니다.");
    }

    const unusable = checkDraftUsable(room, draft, body);

    if (unusable !== null) {
      return reject(unusable);
    }

    const items = rearrangeDraftItems(draft, body);

    if (isRejection(items)) {
      return reject(items);
    }

    draft.jobId = `mock-job-${nextSeq()}`;
    draft.pending = { readyAt: Date.now() + RECALCULATION_MS, ...trimMockOverflow(items, draft.days, room.places, draft.transportMode) };
    draft.adjustRequests.set(idempotencyKey, { requestBody, jobId: draft.jobId });
    return ok(toJobResponse(draft), 202);
  }),

  // API-039 고친 초안 저장. 확정 항목을 모두 지우고 요청한 배치로 새로 넣는다. 저장하면 여행방 version이 1 오른다.
  http.put("*/api/v1/rooms/:roomId/schedule", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const idempotencyKey = request.headers.get(IDEMPOTENCY_HEADER)?.trim();

    if (!idempotencyKey) {
      return fail(400, "C001", "Idempotency-Key 헤더가 필요합니다.");
    }

    const room = findMemberRoom(params.roomId);

    if (isRejection(room)) {
      return reject(room);
    }

    const requestBody = await request.text();
    const body = JSON.parse(requestBody) as SaveScheduleRequestDto;

    const hasRequired = typeof body.draftKey === "string" && typeof body.draftVersion === "number" && typeof body.baseVersion === "number" &&
      Array.isArray(body.days) && Array.isArray(body.unassignedItemIds) && body.startPoint !== undefined && body.endPoint !== undefined;

    if (!hasRequired) {
      return fail(400, "C001", "draftKey·draftVersion·baseVersion·days·unassignedItemIds·startPoint·endPoint는 필수입니다.");
    }

    const draft = findDraft(room, body.draftKey);

    if (draft === undefined) {
      return reject(DRAFT_NOT_FOUND);
    }

    // 같은 초안을 이미 저장했으면 같은 내용이고 그 뒤 여행방이 그대로일 때만 저장 결과를 다시 돌려준다.
    if (draft.applied !== null && draft.draftVersion === body.draftVersion) {
      const replayed = draft.applied.requestBody === requestBody && draft.applied.resultVersion === room.version;
      return replayed ? ok({ schedule: room.schedule, version: room.version } satisfies ScheduleResponseDto) : reject(VERSION_MISMATCH);
    }

    const unusable = checkDraftUsable(room, draft, body);

    if (unusable !== null) {
      return reject(unusable);
    }

    const plan = planSave(room, draft, body);

    if (isRejection(plan)) {
      return reject(plan);
    }

    room.schedule = plan.days.some((day) => {
      return day.items.length > 0;
    }) ? toMockScheduleView(plan.days, plan.unassignedRoomPlaceIds) : null;

    room.version += 1;
    room.scheduleNeedsRecalculation = false;
    draft.applied = { requestBody, resultVersion: room.version };
    const response: ScheduleResponseDto = { schedule: room.schedule, version: room.version };
    return ok(response);
  }),
];
