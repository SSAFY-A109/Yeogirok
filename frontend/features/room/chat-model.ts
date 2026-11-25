import { assertId, ContractMismatchError } from "../../lib/contract";
import { formatServerDateTime, parseServerDateTime, type LocalDate } from "../../lib/date";
import type { ChatReadStateDto } from "./model";

// 여행방 채팅(API-076·077·088, WebSocket `/ws/v1`) DTO와 프레임은 `backend/dev` 12211b2(2026-11-11)의
// ChatController·ChatMessagePage·ChatReadResponse·ChatTicketResponse·ChatFrames·ChatWebSocketHandler와 대조했다.
// 여행방 변경 알림(`ROOM_UPDATED`)은 백엔드 브랜치 `backend/feat/S15P31A109-206-room-realtime-updates` 39d5d96(2026-11-12, 머지 전)의
// ChatFrames.RoomUpdated·`backend/docs/07-chat.md`와 대조했다.

/** 메시지 본문 최대 글자 수(코드 포인트 기준). 넘으면 서버가 C003으로 거절한다. */
export const CHAT_BODY_MAX_LENGTH = 100;

/** `ROOM_UPDATED`가 알려 주는 여행방의 바뀐 부분. 서버는 ROOM → PLACES → SCHEDULE → MEMBERS 순으로 보낸다. */
export const ROOM_CHANGES = ["ROOM", "PLACES", "SCHEDULE", "MEMBERS"] as const;

/** 모르는 값은 `UNKNOWN`으로 흡수한다(규칙 90). 무엇이 바뀌었는지 몰라도 여행방은 다시 받는다. */
export type RoomChange = (typeof ROOM_CHANGES)[number] | "UNKNOWN";

// #region DTO

/** 탈퇴한 회원이 보낸 메시지는 `id`가 `null`이고 이름이 "탈퇴한 사용자"다. */
export type ChatSenderDto = { id: number | null; nickname: string };

export type ChatMessageDto = {
  id: number;
  sender: ChatSenderDto;
  body: string;
  createdAt: string;
  clientMessageId: string | null;
};

/** API-076 응답. `items`는 오래된 → 최신 순이다. `nextAfterMessageId`·`replayThroughMessageId`는 재연결 복구 조회에서만 값이 있다. */
export type ChatMessagePageDto = {
  items: ChatMessageDto[];
  hasMore: boolean;
  nextAfterMessageId: number | null;
  replayThroughMessageId: number | null;
  chatReadState: ChatReadStateDto;
};

export type ChatReadRequestDto = { lastReadMessageId: number };

/** API-088 응답. 더 작은 값을 보내면 서버에 있던 읽은 위치가 그대로 온다. */
export type ChatReadResponseDto = { roomId: number; lastReadMessageId: number | null; hasUnreadMessages: boolean };

export type ChatTicketRequestDto = { roomId: number };

/** API-077 응답. 티켓은 발급 뒤 30초 안에 한 번만 쓸 수 있고, 주소가 아니라 첫 `AUTH` 프레임에 담는다. */
export type ChatTicketResponseDto = { ticket: string; expiresAt: string; connectPath: string; roomId: number };

/** 앱 → 서버 WebSocket 프레임. */
export type ChatClientFrameDto =
  | { type: "AUTH"; ticket: string }
  | { type: "SEND_MESSAGE"; roomId: number; clientMessageId: string; body: string }
  | { type: "PING" };

/** 서버 → 앱 WebSocket 프레임(backend `ChatFrames`). 값이 없는 필드도 `null`로 온다. */
export type ChatServerFrameDto =
  | { type: "READY"; roomId: number; userId: number; latestMessageId: number | null }
  | { type: "MESSAGE_ACK"; roomId: number; clientMessageId: string; replayed: boolean; message: ChatMessageDto }
  | { type: "MESSAGE_CREATED"; roomId: number; message: ChatMessageDto }
  | { type: "PONG"; roomId: number; latestMessageId: number | null }
  | { type: "ROOM_UPDATED"; roomId: number; version: number; changes: string[]; changedBy: number }
  | { type: "ERROR"; roomId: number | null; clientMessageId: string | null; code: string; message: string; retryable: boolean };

// #endregion

// #region 화면 모델

export type ChatMessage = {
  id: number;
  /** 탈퇴한 회원이면 `null`. */
  senderId: number | null;
  senderName: string;
  /** 아바타에 쓰는 이름 첫 글자. */
  senderInitial: string;
  body: string;
  /** 보낸 날짜. 서버 시간대(Asia/Seoul) 기준이다. */
  sentDate: LocalDate;
  /** 보낸 시각 `HH:mm`. 서버 시간대(Asia/Seoul) 기준이다. */
  sentTime: string;
  clientMessageId: string | null;
};

export type ChatReadState = {
  /** 내가 읽은 마지막 메시지. 읽은 적이 없으면 `null`. */
  lastReadMessageId: number | null;
  /** 읽은 위치 뒤에 다른 일행의 메시지가 있는지. */
  hasUnread: boolean;
};

/** 이력 한 쪽. `messages`는 오래된 → 최신 순이다. */
export type ChatMessagePage = { messages: ChatMessage[]; hasMore: boolean; readState: ChatReadState };

/** 재연결 복구 조회(API-076 `afterMessageId`)의 한 쪽. `messages`는 오래된 → 최신 순이다. */
export type ChatReplayPage = {
  messages: ChatMessage[];
  /** 다음 쪽을 받을 때 보낼 `afterMessageId`. 마지막 쪽이면 `null`. */
  nextAfterMessageId: number | null;
  /** 서버가 첫 쪽에서 고정한 복구 상한. 다음 쪽에도 같은 값을 보낸다. */
  throughMessageId: number;
};

export type ChatTicket = {
  ticket: string;
  /** WebSocket 경로(`/ws/v1`). API 호스트에 붙여 연다. */
  connectPath: string;
};

/**
 * 화면이 쓰는 서버 프레임. 모르는 `type`은 `UNKNOWN`으로 흡수한다.
 * `ERROR`는 `code`로만 분기하고 서버 `message`는 쓰지 않는다.
 * `ROOM_UPDATED`는 바뀐 부분만 쓴다. 바뀐 데이터는 실려 오지 않고, `version`은 도착 순서가 바뀔 수 있어 알림을 거르는 데 쓰지 않는다.
 */
export type ChatServerFrame =
  | { type: "READY"; userId: number; latestMessageId: number | null }
  | { type: "MESSAGE_ACK"; clientMessageId: string; message: ChatMessage }
  | { type: "MESSAGE_CREATED"; message: ChatMessage }
  | { type: "PONG"; latestMessageId: number | null }
  | { type: "ROOM_UPDATED"; changes: RoomChange[] }
  | { type: "ERROR"; code: string; clientMessageId: string | null; retryable: boolean }
  | { type: "UNKNOWN" };

/**
 * 보냈지만 저장 완료(`MESSAGE_ACK`)를 아직 받지 못한 내 메시지.
 * `FAILED`는 같은 `clientMessageId`·같은 본문으로 다시 보낼 수 있고, `REJECTED`는 서버가 본문을 거절해 다시 보낼 수 없다.
 */
export type PendingChatMessage = { clientMessageId: string; body: string; state: "SENDING" | "FAILED" | "REJECTED" };

// #endregion

const SEOUL_DATE_END = 10;
const SEOUL_TIME_START = 11;
const SEOUL_TIME_END = 16;

function assertNullableId(value: unknown, field: string): number | null {
  return value === null ? null : assertId(value, field);
}

function assertText(value: unknown, field: string): string {
  if (typeof value !== "string") {
    throw new ContractMismatchError(field);
  }

  return value;
}

export function toChatMessage(dto: ChatMessageDto): ChatMessage {
  const senderName = assertText(dto.sender.nickname, "sender.nickname");
  const sentAt = formatServerDateTime(parseServerDateTime(dto.createdAt, "createdAt"));

  return {
    id: assertId(dto.id, "id"),
    senderId: assertNullableId(dto.sender.id, "sender.id"),
    senderName,
    senderInitial: senderName.slice(0, 1),
    body: assertText(dto.body, "body"),
    sentDate: sentAt.slice(0, SEOUL_DATE_END),
    sentTime: sentAt.slice(SEOUL_TIME_START, SEOUL_TIME_END),
    clientMessageId: dto.clientMessageId === null ? null : assertText(dto.clientMessageId, "clientMessageId"),
  };
}

export function toChatReadState(dto: ChatReadStateDto): ChatReadState {
  if (typeof dto.hasUnreadMessages !== "boolean") {
    throw new ContractMismatchError("chatReadState.hasUnreadMessages");
  }

  return {
    lastReadMessageId: assertNullableId(dto.lastReadMessageId, "chatReadState.lastReadMessageId"),
    hasUnread: dto.hasUnreadMessages,
  };
}

export function toChatMessagePage(dto: ChatMessagePageDto): ChatMessagePage {
  if (!Array.isArray(dto.items)) {
    throw new ContractMismatchError("items");
  }

  if (typeof dto.hasMore !== "boolean") {
    throw new ContractMismatchError("hasMore");
  }

  return { messages: dto.items.map(toChatMessage), hasMore: dto.hasMore, readState: toChatReadState(dto.chatReadState) };
}

/** 재연결 복구 응답. 다음 쪽이 있으면 `nextAfterMessageId`가, 항상 `replayThroughMessageId`가 있어야 한다. */
export function toChatReplayPage(dto: ChatMessagePageDto): ChatReplayPage {
  const { messages, hasMore } = toChatMessagePage(dto);

  return {
    messages,
    nextAfterMessageId: hasMore ? assertId(dto.nextAfterMessageId, "nextAfterMessageId") : null,
    throughMessageId: assertId(dto.replayThroughMessageId, "replayThroughMessageId"),
  };
}

export function toChatTicket(dto: ChatTicketResponseDto): ChatTicket {
  const ticket = assertText(dto.ticket, "ticket");
  const connectPath = assertText(dto.connectPath, "connectPath");

  if (ticket === "") {
    throw new ContractMismatchError("ticket");
  }

  if (!connectPath.startsWith("/")) {
    throw new ContractMismatchError("connectPath");
  }

  return { ticket, connectPath };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** 프레임에 실린 메시지가 객체 모양인지만 본다. 필드 값은 `toChatMessage`가 검증한다. */
function isChatMessageShape(value: unknown): value is ChatMessageDto {
  return isRecord(value) && isRecord(value.sender);
}

function toFrameMessage(value: unknown, field: string): ChatMessage {
  if (!isChatMessageShape(value)) {
    throw new ContractMismatchError(field);
  }

  return toChatMessage(value);
}

function isKnownRoomChange(value: unknown): value is (typeof ROOM_CHANGES)[number] {
  return ROOM_CHANGES.some((change) => {
    return change === value;
  });
}

/** `ROOM_UPDATED.changes`를 읽는다. 서버는 하나 이상을 보낸다. */
function toRoomChanges(value: unknown): RoomChange[] {
  if (!Array.isArray(value) || value.length === 0) {
    throw new ContractMismatchError("ROOM_UPDATED.changes");
  }

  return value.map((change) => {
    return isKnownRoomChange(change) ? change : "UNKNOWN";
  });
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/**
 * 서버가 보낸 WebSocket 텍스트 프레임을 읽는다. 모르는 `type`은 `UNKNOWN`이다.
 * @throws ContractMismatchError JSON 객체가 아니거나, 아는 `type`인데 필드가 계약과 다를 때
 */
export function parseChatServerFrame(text: string): ChatServerFrame {
  const frame = parseJson(text);

  if (!isRecord(frame)) {
    throw new ContractMismatchError("frame");
  }

  switch (frame.type) {
    case "READY":
      return {
        type: "READY",
        userId: assertId(frame.userId, "READY.userId"),
        latestMessageId: assertNullableId(frame.latestMessageId, "READY.latestMessageId"),
      };

    case "MESSAGE_ACK":
      return {
        type: "MESSAGE_ACK",
        clientMessageId: assertText(frame.clientMessageId, "MESSAGE_ACK.clientMessageId"),
        message: toFrameMessage(frame.message, "MESSAGE_ACK.message"),
      };

    case "MESSAGE_CREATED":
      return { type: "MESSAGE_CREATED", message: toFrameMessage(frame.message, "MESSAGE_CREATED.message") };

    case "PONG":
      return { type: "PONG", latestMessageId: assertNullableId(frame.latestMessageId, "PONG.latestMessageId") };

    case "ROOM_UPDATED":
      return { type: "ROOM_UPDATED", changes: toRoomChanges(frame.changes) };

    case "ERROR":
      if (typeof frame.retryable !== "boolean") {
        throw new ContractMismatchError("ERROR.retryable");
      }

      return {
        type: "ERROR",
        code: assertText(frame.code, "ERROR.code"),
        clientMessageId: frame.clientMessageId === null ? null : assertText(frame.clientMessageId, "ERROR.clientMessageId"),
        retryable: frame.retryable,
      };

    default:
      return { type: "UNKNOWN" };
  }
}

/** 이력의 쪽들(가장 최근 쪽이 맨 앞)을 오래된 → 최신 순의 한 목록으로 잇는다. */
export function joinChatPages(pages: readonly ChatMessagePage[]): ChatMessage[] {
  return [...pages].reverse().flatMap((page) => {
    return page.messages;
  });
}

/**
 * 두 목록을 메시지 id로 합쳐 오래된 → 최신 순으로 돌려준다. 같은 id는 하나만 남긴다.
 * 실시간 수신은 순서가 바뀌거나 겹칠 수 있어, 이력·복구·실시간 메시지를 모두 서버 id로 맞춘다.
 */
export function mergeChatMessages(base: readonly ChatMessage[], added: readonly ChatMessage[]): ChatMessage[] {
  const byId = new Map<number, ChatMessage>();

  for (const message of base) {
    byId.set(message.id, message);
  }

  for (const message of added) {
    byId.set(message.id, message);
  }

  return [...byId.values()].sort((left, right) => {
    return left.id - right.id;
  });
}

/** 본문 글자 수. 서버와 같이 코드 포인트로 센다(이모지 하나가 한 글자). */
export function countChatBodyLength(body: string): number {
  return [...body].length;
}

/** 본문을 최대 글자 수까지만 남긴다. 앞뒤 공백은 그대로 둔다. */
export function limitChatBody(body: string): string {
  return countChatBodyLength(body) <= CHAT_BODY_MAX_LENGTH ? body : [...body].slice(0, CHAT_BODY_MAX_LENGTH).join("");
}

/** 보낼 수 있는 본문인지. 비었거나 공백만이거나 최대 글자 수를 넘으면 서버가 C003으로 거절한다. */
export function isSendableChatBody(body: string): boolean {
  return body.trim() !== "" && countChatBodyLength(body) <= CHAT_BODY_MAX_LENGTH;
}
