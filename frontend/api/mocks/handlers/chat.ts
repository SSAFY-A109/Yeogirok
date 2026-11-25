import { delay, http } from "msw";

import type { ChatMessageDto, ChatMessagePageDto, ChatReadResponseDto, ChatTicketResponseDto } from "../../../features/room/chat-model";
import { formatServerDateTime } from "../../../lib/date";
import { createIdempotencyKey, IDEMPOTENCY_HEADER } from "../../idempotency";

import { getMockChatReadState, mockDb } from "../db";
import { chatReadKey, MOCK_CHAT_CONNECT_PATH } from "../fixtures/chat";
import { MOCK_ME, type MockRoom } from "../fixtures/room";
import { fail, MOCK_DELAY_MS, ok, readId, requireMockBearer, roomNotFound } from "./respond";

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 50;
const INTEGER_PATTERN = /^-?\d+$/;

/** 연결 티켓 수명(서버 `yeogirok.chat.ticket-ttl`). */
const TICKET_TTL_MS = 30_000;

type MessageWindow = Omit<ChatMessagePageDto, "chatReadState">;

/** 경로의 여행방을 찾는다. 없으면 404 RO001, 내가 참여자가 아니면 403 RO002 응답을 돌려준다. */
function findMemberRoom(roomId: string | readonly string[] | undefined): MockRoom | Response {
  const id = readId(roomId);
  const room = id === null ? undefined : mockDb.rooms.get(id);

  if (room === undefined) {
    return roomNotFound();
  }

  const isMember = room.members.some((member) => {
    return member.userId === MOCK_ME.userId;
  });

  return isMember ? room : fail(403, "RO002", "여행방 참여자가 아닙니다.");
}

/** 쿼리의 정수 값. 없으면 `null`, 정수가 아니면 `NaN`이다. */
function readIntegerParam(query: URLSearchParams, name: string): number | null {
  const raw = query.get(name);

  if (raw === null) {
    return null;
  }

  return INTEGER_PATTERN.test(raw) ? Number(raw) : Number.NaN;
}

/** 조회 방향 혼용, `afterMessageId` 없는 `throughMessageId`, 범위 밖 id를 찾아 이유를 돌려준다(서버 `validateCursors`, C003). */
function findCursorProblem(before: number | null, after: number | null, through: number | null): string | null {
  if (before !== null && after !== null) {
    return "beforeMessageId 와 afterMessageId 는 함께 보낼 수 없습니다.";
  }

  if (through !== null && after === null) {
    return "throughMessageId 는 afterMessageId 와 함께 보냅니다.";
  }

  if (before !== null && before <= 0) {
    return "beforeMessageId 는 양의 정수여야 합니다.";
  }

  if (after !== null && after < 0) {
    return "afterMessageId 는 0 이상이어야 합니다.";
  }

  if (through !== null && after !== null && through < after) {
    return "throughMessageId 는 afterMessageId 이상이어야 합니다.";
  }

  return null;
}

/** 과거 이력. `before`보다 과거(없으면 전체)에서 최근 `limit`개를 오래된 → 최신 순으로 준다. */
function readHistory(messages: readonly ChatMessageDto[], before: number | null, limit: number): MessageWindow {
  const older = before === null ? messages : messages.filter((message) => {
    return message.id < before;
  });

  return { items: older.slice(-limit), hasMore: older.length > limit, nextAfterMessageId: null, replayThroughMessageId: null };
}

/**
 * 재연결 복구. `after` 초과 `through` 이하를 오래된 순으로 준다. `through`가 없으면 지금 방의 최신 id(없으면 0)를 상한으로 고정한다.
 * 상한이 `after`보다 작으면 `null`이다(C003).
 */
function readReplay(messages: readonly ChatMessageDto[], after: number, through: number | null, limit: number): MessageWindow | null {
  const upperId = through ?? messages[messages.length - 1]?.id ?? 0;

  if (upperId < after) {
    return null;
  }

  const range = messages.filter((message) => {
    return message.id > after && message.id <= upperId;
  });

  const items = range.slice(0, limit);
  const hasMore = range.length > limit;
  return { items, hasMore, nextAfterMessageId: hasMore ? items[items.length - 1].id : null, replayThroughMessageId: upperId };
}

export const chatHandlers = [
  http.get("*/api/v1/rooms/:roomId/messages", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const unauthorized = requireMockBearer(request);

    if (unauthorized) {
      return unauthorized;
    }

    const query = new URL(request.url).searchParams;
    const limit = readIntegerParam(query, "limit") ?? DEFAULT_PAGE_SIZE;
    const before = readIntegerParam(query, "beforeMessageId");
    const after = readIntegerParam(query, "afterMessageId");
    const through = readIntegerParam(query, "throughMessageId");

    if ([limit, before, after, through].some(Number.isNaN)) {
      return fail(400, "C002", "limit·메시지 id는 정수여야 합니다.");
    }

    if (limit < 1 || limit > MAX_PAGE_SIZE) {
      return fail(400, "C007", "페이지 크기가 허용 범위를 벗어났습니다.");
    }

    const cursorProblem = findCursorProblem(before, after, through);

    if (cursorProblem !== null) {
      return fail(400, "C003", cursorProblem);
    }

    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    const messages = mockDb.roomMessages.get(room.id) ?? [];
    const page = after === null ? readHistory(messages, before, limit) : readReplay(messages, after, through, limit);

    if (page === null) {
      return fail(400, "C003", "복구 상한이 afterMessageId 보다 작습니다.");
    }

    // 조회만으로는 읽은 위치를 바꾸지 않는다.
    const response: ChatMessagePageDto = { ...page, chatReadState: getMockChatReadState(room.id, MOCK_ME.userId) };
    return ok(response);
  }),

  http.patch("*/api/v1/rooms/:roomId/messages/read", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const unauthorized = requireMockBearer(request);

    if (unauthorized) {
      return unauthorized;
    }

    const body: unknown = await request.json().catch(() => {
      return null;
    });

    const lastReadMessageId: unknown = typeof body === "object" && body !== null && "lastReadMessageId" in body ? body.lastReadMessageId : null;

    if (lastReadMessageId == null) {
      return fail(400, "C001", "lastReadMessageId는 필수입니다.");
    }

    if (typeof lastReadMessageId !== "number" || !Number.isSafeInteger(lastReadMessageId)) {
      return fail(400, "C002", "lastReadMessageId는 정수여야 합니다.");
    }

    if (lastReadMessageId <= 0) {
      return fail(400, "C003", "lastReadMessageId 는 양의 정수여야 합니다.");
    }

    const room = findMemberRoom(params.roomId);

    if (room instanceof Response) {
      return room;
    }

    const isRoomMessage = (mockDb.roomMessages.get(room.id) ?? []).some((message) => {
      return message.id === lastReadMessageId;
    });

    if (!isRoomMessage) {
      return fail(400, "C003", "이 여행방의 메시지가 아닙니다.");
    }

    // 더 작은 값이 오면 기존 읽은 위치를 유지한다.
    const key = chatReadKey(room.id, MOCK_ME.userId);
    mockDb.chatLastReadIds.set(key, Math.max(mockDb.chatLastReadIds.get(key) ?? 0, lastReadMessageId));

    const readState = getMockChatReadState(room.id, MOCK_ME.userId);

    const response: ChatReadResponseDto = {
      roomId: room.id,
      lastReadMessageId: readState.lastReadMessageId,
      hasUnreadMessages: readState.hasUnreadMessages,
    };

    return ok(response);
  }),

  http.post("*/api/v1/realtime/tickets", async ({ request }) => {
    await delay(MOCK_DELAY_MS);
    const unauthorized = requireMockBearer(request);

    if (unauthorized) {
      return unauthorized;
    }

    const idempotencyKey = request.headers.get(IDEMPOTENCY_HEADER)?.trim();

    const body: unknown = await request.json().catch(() => {
      return null;
    });

    const roomId: unknown = typeof body === "object" && body !== null && "roomId" in body ? body.roomId : null;

    if (!idempotencyKey || roomId == null) {
      return fail(400, "C001", "Idempotency-Key와 roomId는 필수입니다.");
    }

    if (typeof roomId !== "number" || !Number.isSafeInteger(roomId)) {
      return fail(400, "C002", "roomId는 정수여야 합니다.");
    }

    const room = findMemberRoom(String(roomId));

    if (room instanceof Response) {
      return room;
    }

    // 같은 멱등키로 다시 오면 처음 발급한 티켓을 돌려준다. 이미 썼거나 만료된 티켓일 수 있다.
    const requestKey = `${room.id}:${idempotencyKey}`;
    const issued = mockDb.chatTicketRequests.get(requestKey);

    if (issued !== undefined) {
      return ok(issued, 201);
    }

    const response: ChatTicketResponseDto = {
      ticket: createIdempotencyKey(),
      expiresAt: formatServerDateTime(new Date(Date.now() + TICKET_TTL_MS)),
      connectPath: MOCK_CHAT_CONNECT_PATH,
      roomId: room.id,
    };

    mockDb.chatTickets.set(response.ticket, { userId: MOCK_ME.userId, roomId: room.id });
    mockDb.chatTicketRequests.set(requestKey, response);
    return ok(response, 201);
  }),
];
