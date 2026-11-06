import { delay, http } from "msw";

import type { ApplyScheduleDraftRequestDto, CreateScheduleDraftRequestDto, RoomScheduleDto, ScheduleDraftDto } from "../../../features/schedule/model";

import { mockDb, syncRoomDerivedFields, type MockScheduleDraft } from "../db";
import { planSchedule } from "../fixtures/schedule";
import { fail, MOCK_DELAY_MS, ok, readId, roomNotFound } from "./respond";

/** 처음 만드는 초안은 처리 중 화면을 볼 수 있게 오래, "다시 계산"은 짧게 걸린다. */
const NEW_DRAFT_MS = 3_000;
const RECALCULATION_MS = 800;
const IDEMPOTENCY_HEADER = "idempotency-key";
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

function nowText(): string {
  const now = new Date();

  const pad = (value: number) => {
    return String(value).padStart(2, "0");
  };

  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}+09`;
}

/** 요청 형식과 시각을 확인한다. 문제가 없으면 `null`. */
function validateDraftRequest(body: CreateScheduleDraftRequestDto, roomPlaceIds: ReadonlySet<number>, tripDates: readonly string[]): { code: string; message: string } | null {
  if (!Array.isArray(body.days) || body.days.length === 0 || !["RELAXED", "NORMAL", "PACKED"].includes(body.pace)) {
    return { code: "C001", message: "pace와 days는 필수입니다." };
  }

  for (const day of body.days) {
    if (!tripDates.includes(day.date)) {
      return { code: "C003", message: `여행 기간 밖의 날짜입니다: ${day.date}` };
    }

    if (!TIME_PATTERN.test(day.startTime) || !TIME_PATTERN.test(day.endTime) || day.startTime >= day.endTime) {
      return { code: "C016", message: "시작 시각은 종료 시각보다 앞이어야 합니다." };
    }

    if (day.stops !== null && day.stops.some((stop) => {
      return !roomPlaceIds.has(stop.roomPlaceId);
    })) {
      return { code: "C003", message: "여행방에 없는 장소입니다." };
    }
  }

  return null;
}

function tripDatesOf(startDate: string | null, endDate: string | null): string[] {
  if (startDate === null || endDate === null) {
    return [];
  }

  const dates: string[] = [];
  const cursor = new Date(`${startDate}T00:00:00Z`);
  const end = new Date(`${endDate}T00:00:00Z`);

  while (cursor <= end) {
    dates.push(cursor.toISOString().slice(0, 10));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }

  return dates;
}

function toDraftDto(draft: MockScheduleDraft): ScheduleDraftDto {
  const ready = Date.now() >= draft.readyAt;

  if (!ready) {
    return { draftId: draft.draftId, status: "PROCESSING", failureCode: null, baseVersion: draft.baseVersion, schedule: null };
  }

  return draft.failureCode === null
    ? { draftId: draft.draftId, status: "COMPLETED", failureCode: null, baseVersion: draft.baseVersion, schedule: draft.schedule }
    : { draftId: draft.draftId, status: "FAILED", failureCode: draft.failureCode, baseVersion: draft.baseVersion, schedule: null };
}

export const scheduleHandlers = [
  http.get("*/api/v1/rooms/:roomId/schedule", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const roomId = readId(params.roomId);
    const room = roomId === null ? undefined : mockDb.rooms.get(roomId);
    return room === undefined ? roomNotFound() : ok(room.schedule);
  }),

  // API-037 초안 만들기(작업). 같은 멱등키·같은 내용이면 처음 만든 작업을, 다른 내용이면 C009를 돌려준다.
  http.post("*/api/v1/rooms/:roomId/schedule-drafts", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const roomId = readId(params.roomId);
    const room = roomId === null ? undefined : mockDb.rooms.get(roomId);

    if (roomId === null || room === undefined) {
      return roomNotFound();
    }

    const idempotencyKey = request.headers.get(IDEMPOTENCY_HEADER);

    if (idempotencyKey === null || idempotencyKey.trim() === "") {
      return fail(400, "C001", "Idempotency-Key 헤더가 필요합니다.");
    }

    const requestBody = await request.text();
    const body = JSON.parse(requestBody) as CreateScheduleDraftRequestDto;

    const existing = [...mockDb.scheduleDrafts.values()].find((draft) => {
      return draft.roomId === roomId && draft.idempotencyKey === idempotencyKey;
    });

    if (existing !== undefined) {
      return existing.requestBody === requestBody
        ? ok({ draftId: existing.draftId, status: toDraftDto(existing).status }, 202)
        : fail(409, "C009", "같은 멱등키로 다른 내용의 요청을 보냈습니다.");
    }

    const roomPlaceIds = new Set(room.places.map((place) => {
      return place.roomPlaceId;
    }));

    const invalid = validateDraftRequest(body, roomPlaceIds, tripDatesOf(room.detail.startDate, room.detail.endDate));

    if (invalid !== null) {
      return fail(400, invalid.code, invalid.message);
    }

    const recalculation = body.days.some((day) => {
      return day.stops !== null;
    });

    const draft: MockScheduleDraft = {
      draftId: mockDb.nextScheduleDraftId,
      roomId,
      idempotencyKey,
      requestBody,
      baseVersion: room.schedule?.version ?? null,
      readyAt: Date.now() + (recalculation ? RECALCULATION_MS : NEW_DRAFT_MS),
      schedule: room.places.length === 0 ? null : planSchedule({ roomPlaces: room.places, transportMode: room.detail.transportMode, request: body }),
      failureCode: room.places.length === 0 ? "SC_NO_PLACES" : null,
    };

    mockDb.scheduleDrafts.set(draft.draftId, draft);
    mockDb.nextScheduleDraftId += 1;
    return ok({ draftId: draft.draftId, status: "PROCESSING" }, 202);
  }),

  // API-038 초안 조회. 준비 시각 전에는 처리 중으로 보인다.
  http.get("*/api/v1/rooms/:roomId/schedule-drafts/:draftId", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const roomId = readId(params.roomId);
    const draftId = readId(params.draftId);
    const draft = draftId === null ? undefined : mockDb.scheduleDrafts.get(draftId);

    if (roomId === null || !mockDb.rooms.has(roomId)) {
      return roomNotFound();
    }

    if (draft === undefined || draft.roomId !== roomId) {
      return fail(404, "SC404", "일정 초안을 찾을 수 없습니다.");
    }

    return ok(toDraftDto(draft));
  }),

  // API-039 초안 적용. 초안을 만든 뒤 일정이 바뀌었으면(버전이 다르면) C008.
  http.put("*/api/v1/rooms/:roomId/schedule", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const roomId = readId(params.roomId);
    const room = roomId === null ? undefined : mockDb.rooms.get(roomId);

    if (roomId === null || room === undefined) {
      return roomNotFound();
    }

    const body = (await request.json()) as ApplyScheduleDraftRequestDto;
    const draft = mockDb.scheduleDrafts.get(body.draftId);

    if (draft === undefined || draft.roomId !== roomId || toDraftDto(draft).status !== "COMPLETED" || draft.schedule === null) {
      return fail(400, "C003", "적용할 수 없는 초안입니다.");
    }

    const currentVersion = room.schedule?.version ?? null;

    if (body.baseVersion !== currentVersion) {
      return fail(409, "C008", "일정이 그사이 바뀌었습니다.");
    }

    const schedule: RoomScheduleDto = { ...draft.schedule, version: (currentVersion ?? 0) + 1, createdAt: nowText() };
    room.schedule = schedule;
    syncRoomDerivedFields(room);
    return ok(schedule);
  }),
];
