import { delay, http } from "msw";

import { IDEMPOTENCY_HEADER } from "../../idempotency";
import type {
  AcceptRoomInvitationResponseDto,
  AddRoomPlacesResponseDto,
  AffectedScheduleItemDto,
  CreateRoomResponseDto,
  ImportCoursePlacesResponseDto,
  RemoveRoomPlaceResponseDto,
  RestoreRoomPlaceResponseDto,
  RoomInvitationDto,
  RoomPlaceDto,
  RoomPlacesResponseDto,
  RoomsResponseDto,
  UpdateRoomResponseDto,
} from "../../../features/room/model";
import { formatServerDateTime, todayLocalDate } from "../../../lib/date";

import { getMockChatReadState, mockDb } from "../db";
import { findMockCourse } from "../fixtures/course";
import { findCatalogPlace, MOCK_PLACE_PROVIDER } from "../fixtures/place";
import {
  createRoomPlaceFixture,
  getActivePlaces,
  getMockChatMessages,
  getMockChatReadState,
  MOCK_ME,
  ROOM_MEMBER_LIMIT,
  toRoomDetail,
  toRoomListItem,
  type MockRoom,
} from "../fixtures/room";
import { fail, MOCK_DELAY_MS, ok, readId, requireMockBearer, roomNotFound } from "./respond";

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const TITLE_MAX_LENGTH = 200;
const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 50;
const PLACE_STATES = ["ACTIVE", "REMOVED", "ALL"];

type JsonBody = Record<string, unknown>;

async function readBody(request: Request): Promise<JsonBody> {
  const body: unknown = await request.json().catch(() => {
    return null;
  });

  return typeof body === "object" && body !== null ? (body as JsonBody) : {};
}

function isMember(room: MockRoom): boolean {
  return room.members.some((member) => {
    return member.userId === MOCK_ME.userId;
  });
}

/** 경로의 여행방을 찾는다. 없으면 404 RO001, 내가 참여자가 아니면 403 RO002 응답을 돌려준다. */
function findMemberRoom(roomId: string | readonly string[] | undefined): MockRoom | Response {
  const id = readId(roomId);
  const room = id === null ? undefined : mockDb.rooms.get(id);

  if (room === undefined) {
    return roomNotFound();
  }

  return isMember(room) ? room : fail(403, "RO002", "여행방 참여자가 아닙니다.");
}

function versionMismatch() {
  return fail(409, "C004", "리소스 버전이 일치하지 않습니다.");
}

/** 후보 목록이 바뀌면 version 을 올리고, 확정 일정이 있으면 재계산 필요를 켠다(서버 `RoomVersions`). */
function touchAfterPlacesChanged(room: MockRoom): void {
  room.version += 1;

  if (room.schedule !== null) {
    room.scheduleNeedsRecalculation = true;
  }
}

/** 날짜는 둘 다 정하거나 둘 다 비운다. 형식·순서가 맞는지 본다. */
function isValidDateRange(startDate: unknown, endDate: unknown): boolean {
  if (startDate === null && endDate === null) {
    return true;
  }

  return (
    typeof startDate === "string" && typeof endDate === "string" &&
    DATE_PATTERN.test(startDate) && DATE_PATTERN.test(endDate) && startDate <= endDate
  );
}

function isTransportMode(value: unknown): value is MockRoom["transportMode"] {
  return value === "CAR" || value === "TRANSIT";
}

function isValidTitle(value: string): boolean {
  const length = [...value.trim()].length;
  return length >= 1 && length <= TITLE_MAX_LENGTH;
}

/** 새 기간 밖으로 빠지는 일정 항목. 날짜를 비우면(일정이 있는 방은 RO010) 부르지 않는다. */
function getAffectedItems(room: MockRoom, startDate: string, endDate: string): AffectedScheduleItemDto[] {
  if (room.schedule === null) {
    return [];
  }

  return room.schedule.days
    .filter((day) => {
      return day.date < startDate || day.date > endDate;
    })
    .flatMap((day) => {
      return day.items.map((item) => {
        const place = room.places.find((candidate) => {
          return candidate.id === item.roomPlaceId;
        });

        return { scheduleItemId: item.id, roomPlaceId: item.roomPlaceId, name: place?.place.name ?? "", visitDate: day.date };
      });
    });
}

function toAddedPlace({ id, place, sourceType, sourceRecordId, addedBy }: RoomPlaceDto): RoomPlaceDto {
  return { id, place, sourceType, sourceRecordId, addedBy };
}

export const roomHandlers = [
  http.get("*/api/v1/rooms/:roomId/messages", ({ params, request }) => {
    const unauthorized = requireMockBearer(request);

    if (unauthorized) {
      return unauthorized;
    }

    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    const query = new URL(request.url).searchParams;

    const raw = ["beforeMessageId", "afterMessageId", "throughMessageId", "limit"].map((name) => {
      return query.get(name);
    });

    if (raw.some((value) => {
      return value !== null && (!/^\d+$/.test(value) || !Number.isSafeInteger(Number(value)));
    })) {
      return fail(400, "C002", "정수 형식이 올바르지 않습니다.");
    }

    const [before, after, through, requestedLimit] = raw.map((value) => {
      return value === null ? undefined : Number(value);
    });

    const limit = requestedLimit ?? DEFAULT_PAGE_SIZE;

    if (limit < 1 || limit > MAX_PAGE_SIZE) {
      return fail(400, "C007", "페이지 크기가 범위를 벗어났습니다.");
    }

    const messages = getMockChatMessages(room);
    const upper = through ?? messages.at(-1)?.id ?? 0;

    if (before === 0 || (after !== undefined && (before !== undefined || upper < after)) ||
      (after === undefined && through !== undefined)) {
      return fail(400, "C003", "채팅 조회 범위가 올바르지 않습니다.");
    }

    const matching = after === undefined
      ? messages.filter(({ id }) => {
        return before === undefined || id < before;
      })
      : messages.filter(({ id }) => {
        return id > after && id <= upper;
      });

    const items = after === undefined ? matching.slice(-limit) : matching.slice(0, limit);
    const hasMore = matching.length > items.length;
    return ok({
      items,
      hasMore,
      nextAfterMessageId: after !== undefined && hasMore ? items.at(-1)?.id ?? null : null,
      replayThroughMessageId: after === undefined ? null : upper,
      chatReadState: getMockChatReadState(room),
    });
  }),

  http.patch("*/api/v1/rooms/:roomId/messages/read", async ({ params, request }) => {
    const unauthorized = requireMockBearer(request);

    if (unauthorized) {
      return unauthorized;
    }

    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    const { lastReadMessageId } = await readBody(request);

    if (lastReadMessageId === undefined) {
      return fail(400, "C001", "읽은 메시지 ID가 필요합니다.");
    }

    if (typeof lastReadMessageId !== "number" || !Number.isSafeInteger(lastReadMessageId)) {
      return fail(400, "C002", "읽은 메시지 ID는 정수여야 합니다.");
    }

    if (!getMockChatMessages(room).some(({ id }) => {
      return id === lastReadMessageId;
    })) {
      return fail(400, "C003", "이 방에서 확인할 수 없는 메시지입니다.");
    }

    room.chatLastReadMessageId = Math.max(room.chatLastReadMessageId ?? 0, lastReadMessageId);
    return ok({ roomId: room.id, ...getMockChatReadState(room) });
  }),

  http.get("*/api/v1/rooms", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const url = new URL(request.url);
    const limit = Number(url.searchParams.get("limit") ?? DEFAULT_PAGE_SIZE);
    const offset = Number(url.searchParams.get("cursor") ?? 0);

    if (!Number.isInteger(limit) || limit < 1 || limit > MAX_PAGE_SIZE) {
      return fail(400, "C007", "페이지 크기가 허용 범위를 벗어났습니다.");
    }

    if (!Number.isInteger(offset) || offset < 0) {
      return fail(400, "C006", "페이지 커서가 유효하지 않습니다.");
    }

    const rooms = [...mockDb.rooms.values()].filter(isMember);

    const items = rooms.slice(offset, offset + limit).map((room) => {
      return toRoomListItem(room, getMockChatReadState(room.id, MOCK_ME.userId));
    });

    const nextOffset = offset + items.length;
    const response: RoomsResponseDto = { items, nextCursor: nextOffset < rooms.length ? String(nextOffset) : null };
    return ok(response);
  }),

  http.post("*/api/v1/rooms", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const unauthorized = requireMockBearer(request);

    if (unauthorized) {
      return unauthorized;
    }

    const key = request.headers.get(IDEMPOTENCY_HEADER)?.trim();
    const body = await readBody(request);

    if (!key || body.title == null || body.transportMode == null) {
      return fail(400, "C001", "Idempotency-Key, title, transportMode는 필수입니다.");
    }

    if (typeof body.title !== "string" || typeof body.transportMode !== "string") {
      return fail(400, "C002", "title과 transportMode는 문자열이어야 합니다.");
    }

    const title = body.title.trim();
    const startDate = body.startDate ?? null;
    const endDate = body.endDate ?? null;
    const { transportMode } = body;

    if (!isValidTitle(title) || !isTransportMode(transportMode)) {
      return fail(400, "C003", "여행방 이름 또는 이동 수단이 올바르지 않습니다.");
    }

    const requestBody = JSON.stringify({ title, startDate, endDate, transportMode });
    const previous = mockDb.roomCreateRequests.get(key);

    if (previous) {
      return previous.requestBody === requestBody ? ok(previous.response, 201) : fail(409, "C005", "같은 멱등키로 다른 요청을 보냈습니다.");
    }

    if (!isValidDateRange(startDate, endDate) || (typeof startDate === "string" && startDate < todayLocalDate())) {
      return fail(400, "C012", "시작·종료 날짜의 범위나 순서가 올바르지 않습니다.");
    }

    const id = mockDb.nextRoomId;
    mockDb.nextRoomId += 1;

    mockDb.rooms.set(id, {
      id,
      title,
      startDate: typeof startDate === "string" ? startDate : null,
      endDate: typeof endDate === "string" ? endDate : null,
      transportMode,
      members: [MOCK_ME],
      places: [],
      recordIds: [],
      version: 1,
      schedule: null,
      scheduleNeedsRecalculation: false,
      inviteToken: `mock-invite-${id}`,
    });

    const response: CreateRoomResponseDto = { id, version: 1, isCreator: true };
    mockDb.roomCreateRequests.set(key, { requestBody, response });
    return ok(response, 201);
  }),

  http.get("*/api/v1/rooms/:roomId", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const room = findMemberRoom(params.roomId);
    return room instanceof Response ? room : ok(toRoomDetail(room, getMockChatReadState(room.id, MOCK_ME.userId)));
  }),

  http.patch("*/api/v1/rooms/:roomId", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    const body = await readBody(request);

    if (body.version === undefined) {
      return fail(400, "C001", "version은 필수입니다.");
    }

    if (body.title === null || body.transportMode === null) {
      return fail(400, "C003", "title·transportMode는 null로 바꿀 수 없습니다.");
    }

    if ((body.title !== undefined && typeof body.title !== "string") || typeof body.version !== "number") {
      return fail(400, "C002", "요청 필드의 형식이 올바르지 않습니다.");
    }

    if (body.title !== undefined && !isValidTitle(body.title)) {
      return fail(400, "C003", "여행방 이름이 올바르지 않습니다.");
    }

    if (body.transportMode !== undefined && !isTransportMode(body.transportMode)) {
      return fail(400, "C003", "transportMode: CAR 또는 TRANSIT 이어야 합니다.");
    }

    const datesGiven = "startDate" in body || "endDate" in body;

    if (datesGiven && (!("startDate" in body) || !("endDate" in body) || !isValidDateRange(body.startDate, body.endDate))) {
      return fail(400, "C012", "시작·종료 날짜의 범위나 순서가 올바르지 않습니다.");
    }

    if (body.version !== room.version) {
      return versionMismatch();
    }

    const startDate = datesGiven ? (body.startDate as string | null) : room.startDate;
    const endDate = datesGiven ? (body.endDate as string | null) : room.endDate;

    if (room.schedule !== null && (startDate === null || endDate === null)) {
      return fail(409, "RO010", "일정이 있는 여행방의 날짜는 삭제할 수 없습니다.");
    }

    const affectedItems = startDate === null || endDate === null ? [] : getAffectedItems(room, startDate, endDate);

    if (body.previewOnly === true) {
      const preview: UpdateRoomResponseDto = {
        id: room.id,
        version: room.version,
        applied: false,
        scheduleNeedsRecalculation: room.scheduleNeedsRecalculation,
        affectedItems,
        schedule: null,
      };

      return ok(preview);
    }

    room.title = typeof body.title === "string" ? body.title.trim() : room.title;
    room.startDate = startDate;
    room.endDate = endDate;
    room.transportMode = isTransportMode(body.transportMode) ? body.transportMode : room.transportMode;
    room.version += 1;

    // 목은 새 기간 밖의 날만 지우고 남은 날의 시각·이동 시간은 다시 계산하지 않는다.
    if (room.schedule !== null && startDate !== null && endDate !== null) {
      room.schedule = {
        ...room.schedule,

        days: room.schedule.days.filter((day) => {
          return day.date >= startDate && day.date <= endDate;
        }),
      };
    }

    const response: UpdateRoomResponseDto = {
      id: room.id,
      version: room.version,
      applied: true,
      scheduleNeedsRecalculation: room.scheduleNeedsRecalculation,
      affectedItems,
      schedule: room.schedule,
    };

    return ok(response);
  }),

  http.delete("*/api/v1/rooms/:roomId/members/me", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    room.members = room.members.filter((member) => {
      return member.userId !== MOCK_ME.userId;
    });

    // 마지막 참여자가 나가면 여행방을 지운다. 별도 방 삭제 API는 없다.
    if (room.members.length === 0) {
      mockDb.rooms.delete(room.id);
    }

    return ok(null);
  }),

  http.post("*/api/v1/rooms/:roomId/invitations", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);

    if (!request.headers.get(IDEMPOTENCY_HEADER)?.trim()) {
      return fail(400, "C001", "Idempotency-Key가 필요합니다.");
    }

    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    // 링크는 여행방마다 하나이고 만료가 없다. 다시 불러도 같은 링크다.
    const response: RoomInvitationDto = {
      invitationId: room.id,
      inviteToken: room.inviteToken,
      expiresAt: null,
      inviteUrl: `https://yeogirok.app/invite/${room.inviteToken}`,
    };

    return ok(response);
  }),

  http.post("*/api/v1/room-invitations/accept", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const body = await readBody(request);

    if (!request.headers.get(IDEMPOTENCY_HEADER)?.trim() || body.inviteToken == null) {
      return fail(400, "C001", "Idempotency-Key와 inviteToken이 필요합니다.");
    }

    const room = [...mockDb.rooms.values()].find((candidate) => {
      return candidate.inviteToken === body.inviteToken;
    });

    if (room === undefined) {
      return fail(404, "RO005", "여행방 초대 토큰이 유효하지 않습니다.");
    }

    const me = room.members.find((member) => {
      return member.userId === MOCK_ME.userId;
    });

    // 이미 참여한 방이면 인원을 늘리지 않고 같은 결과를 준다.
    if (me === undefined) {
      if (room.members.length >= ROOM_MEMBER_LIMIT) {
        return fail(409, "RO007", "여행방 참여 인원이 가득 찼습니다.");
      }

      room.members = [...room.members, { ...MOCK_ME, isCreator: false }];
    }

    const response: AcceptRoomInvitationResponseDto = { roomId: room.id, membershipStatus: "JOINED", isCreator: me?.isCreator ?? false };
    return ok(response);
  }),

  http.get("*/api/v1/rooms/:roomId/places", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    const state = new URL(request.url).searchParams.get("state") ?? "ACTIVE";

    if (!PLACE_STATES.includes(state)) {
      return fail(400, "C003", "state: ACTIVE, REMOVED, ALL 중 하나여야 합니다.");
    }

    const items = room.places.filter((place) => {
      return state === "ALL" || (state === "REMOVED") === (place.removedAt != null);
    });

    const response: RoomPlacesResponseDto = { items, version: room.version };
    return ok(response);
  }),

  http.post("*/api/v1/rooms/:roomId/places", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const key = request.headers.get(IDEMPOTENCY_HEADER)?.trim();
    const body = await readBody(request);

    if (!key || !Array.isArray(body.items)) {
      return fail(400, "C001", "Idempotency-Key와 items가 필요합니다.");
    }

    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    const scopedKey = `${room.id}:${key}`;
    const requestBody = JSON.stringify(body.items);
    const previous = mockDb.roomPlaceRequests.get(scopedKey);

    if (previous) {
      return previous.requestBody === requestBody ? ok(previous.response, 201) : fail(409, "C005", "같은 멱등키로 다른 요청을 보냈습니다.");
    }

    const items = body.items as { placeRef?: { provider?: unknown; providerPlaceId?: unknown }; sourceType?: unknown; recommendationRequestId?: unknown; rank?: unknown }[];

    const isValid = items.length > 0 && items.every((item) => {
      const hasRef = item.placeRef?.provider === MOCK_PLACE_PROVIDER && typeof item.placeRef.providerPlaceId === "string";
      const hasAiFields = typeof item.recommendationRequestId === "number" && typeof item.rank === "number";
      return hasRef && (item.sourceType === "SEARCH" || (item.sourceType === "AI" && hasAiFields));
    });

    if (!isValid || items.some((item) => {
      return findCatalogPlace(String(item.placeRef?.providerPlaceId)) === null;
    })) {
      return fail(400, "C003", "담을 장소가 올바르지 않습니다.");
    }

    // 같은 장소를 새 요청으로 다시 담으면 별도 후보 행을 만든다(팀 확정 정책).
    const added = items.map((item) => {
      const id = mockDb.nextRoomPlaceId;
      mockDb.nextRoomPlaceId += 1;
      return createRoomPlaceFixture(id, String(item.placeRef?.providerPlaceId), item.sourceType === "AI" ? "AI" : "SEARCH");
    });

    room.places = [...room.places, ...added];
    touchAfterPlacesChanged(room);
    const response: AddRoomPlacesResponseDto = { added: added.map(toAddedPlace), addedCount: added.length, version: room.version };
    mockDb.roomPlaceRequests.set(scopedKey, { requestBody, response });
    return ok(response, 201);
  }),

  http.post("*/api/v1/rooms/:roomId/place-imports", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const unauthorized = requireMockBearer(request);

    if (unauthorized) {
      return unauthorized;
    }

    const key = request.headers.get(IDEMPOTENCY_HEADER)?.trim();
    const body = await readBody(request);

    if (!key || body.recordId === undefined) {
      return fail(400, "C001", "Idempotency-Key와 recordId가 필요합니다.");
    }

    if (!Array.isArray(body.visitIds) || body.visitIds.length === 0) {
      return fail(400, "CO006", "가져올 코스 장소를 선택하지 않았습니다.");
    }

    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    const recordId = Number(body.recordId);
    const course = findMockCourse(recordId);

    if (course === null) {
      return fail(404, "CO001", "공개 코스를 찾을 수 없습니다.");
    }

    const scopedKey = `${room.id}:${key}`;
    const requestBody = JSON.stringify({ recordId, visitIds: body.visitIds });
    const previous = mockDb.courseImportRequests.get(scopedKey);

    if (previous) {
      return previous.requestBody === requestBody ? ok(previous.response, 201) : fail(409, "C005", "같은 멱등키로 다른 요청을 보냈습니다.");
    }

    const visits = new Map(course.detail.days.flatMap((day) => {
      return day.items.map((visit) => {
        return [visit.visitId, visit] as const;
      });
    }));

    const selected = (body.visitIds as unknown[]).map((visitId) => {
      return visits.get(Number(visitId));
    });

    if (!selected.every((visit) => {
      return visit !== undefined;
    })) {
      return fail(400, "CO007", "선택한 방문이 이 코스에 속하지 않습니다.");
    }

    const added: ImportCoursePlacesResponseDto["added"] = [];

    for (const visit of selected) {
      const id = mockDb.nextRoomPlaceId;
      mockDb.nextRoomPlaceId += 1;
      room.places.push(createRoomPlaceFixture(id, String(visit.place.providerPlaceId), "COURSE", MOCK_ME, recordId));

      added.push({
        id,
        place: { ...visit.place, regionCode: course.regionCode, regionName: course.summary.regionName },
        sourceType: "COURSE",
        sourceRecordId: recordId,
        addedBy: MOCK_ME.userId,
      });
    }

    touchAfterPlacesChanged(room);
    const response = { added, version: room.version };
    mockDb.courseImportRequests.set(scopedKey, { requestBody, response });
    return ok(response, 201);
  }),

  http.delete("*/api/v1/rooms/:roomId/places/:roomPlaceId", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    const versionParam = new URL(request.url).searchParams.get("version");

    if (versionParam === null) {
      return fail(400, "C001", "version은 필수입니다.");
    }

    const roomPlaceId = readId(params.roomPlaceId);

    const place = getActivePlaces(room).find((candidate) => {
      return candidate.id === roomPlaceId;
    });

    if (place === undefined) {
      return fail(404, "RO009", "요청한 후보 장소가 해당 여행방에 없습니다.");
    }

    if (Number(versionParam) !== room.version) {
      return versionMismatch();
    }

    // 후보 목록에서만 뺀다. 확정 일정의 항목·순서·시간은 그대로 둔다.
    place.removedAt = formatServerDateTime(new Date());
    touchAfterPlacesChanged(room);

    const response: RemoveRoomPlaceResponseDto = {
      removed: true,
      version: room.version,
      scheduleNeedsRecalculation: room.scheduleNeedsRecalculation,
      undoExpiresAt: null,
    };

    return ok(response);
  }),

  http.patch("*/api/v1/rooms/:roomId/places/:roomPlaceId", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    const body = await readBody(request);

    if (body.version === undefined || body.action === undefined) {
      return fail(400, "C001", "version과 action은 필수입니다.");
    }

    if (body.action !== "RESTORE") {
      return fail(400, "C003", "action: RESTORE 여야 합니다.");
    }

    const roomPlaceId = readId(params.roomPlaceId);

    const place = room.places.find((candidate) => {
      return candidate.id === roomPlaceId && candidate.removedAt != null;
    });

    if (place === undefined) {
      return fail(404, "RO009", "요청한 후보 장소가 해당 여행방에 없습니다.");
    }

    if (body.version !== room.version) {
      return versionMismatch();
    }

    place.removedAt = null;
    touchAfterPlacesChanged(room);

    const response: RestoreRoomPlaceResponseDto = {
      restored: true,
      roomPlaceId: place.id,
      version: room.version,
      scheduleNeedsRecalculation: room.scheduleNeedsRecalculation,
    };

    return ok(response);
  }),
];
