import type { ChatTransportFactory } from "../../features/room/api/chat-transport";
import type { ChatMessageDto, ChatSenderDto, ChatServerFrameDto } from "../../features/room/chat-model";
import { formatServerDateTime } from "../../lib/date";

import { mockDb } from "./db";
import { MOCK_CHAT_CONNECT_PATH, MOCK_CHAT_REPLIES } from "./fixtures/chat";

/** 연결이 열리기까지와 프레임이 도착하기까지의 지연(밀리초). 연결 중·보내는 중 상태를 눈으로 확인할 수 있을 만큼만 둔다. */
const OPEN_DELAY_MS = 300;
const FRAME_DELAY_MS = 200;

/** 내가 보낸 뒤 다른 일행의 답장이 오기까지의 지연(밀리초). */
const REPLY_DELAY_MS = 1_500;

/** 서버 `RoomMessage`의 본문·`clientMessageId` 최대 글자 수(코드 포인트). */
const BODY_MAX_LENGTH = 100;
const CLIENT_MESSAGE_ID_MAX_LENGTH = 100;

type Member = { userId: number; roomId: number };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function parseFrame(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function countCodePoints(text: string): number {
  return [...text].length;
}

/** 여행방이 없으면 RO001, 참여자가 아니면 RO002. 연결·전송·PING마다 다시 확인한다(서버 `checkAccess`). */
function findAccessProblem({ userId, roomId }: Member): "RO001" | "RO002" | null {
  const room = mockDb.rooms.get(roomId);

  if (room === undefined) {
    return "RO001";
  }

  const isMember = room.members.some((member) => {
    return member.userId === userId;
  });

  return isMember ? null : "RO002";
}

function getLatestMessageId(roomId: number): number | null {
  const messages = mockDb.roomMessages.get(roomId) ?? [];
  return messages[messages.length - 1]?.id ?? null;
}

/** 메시지 id는 방과 상관없이 이어진다. */
function saveMessage(roomId: number, sender: ChatSenderDto, body: string, clientMessageId: string): ChatMessageDto {
  let latestId = 0;

  for (const messages of mockDb.roomMessages.values()) {
    latestId = Math.max(latestId, messages[messages.length - 1]?.id ?? 0);
  }

  const message: ChatMessageDto = { id: latestId + 1, sender, body, createdAt: formatServerDateTime(new Date()), clientMessageId };
  mockDb.roomMessages.set(roomId, [...(mockDb.roomMessages.get(roomId) ?? []), message]);
  return message;
}

/**
 * 개발 환경에서 WebSocket 대신 쓰는 채팅 연결. 서버 `ChatWebSocketHandler`처럼 `AUTH` 티켓을 한 번 쓰고, 메시지를 목 저장소에 넣고,
 * 저장 완료·새 메시지·PONG·ERROR 프레임을 돌려준다. 내가 보내면 다른 일행 한 명이 잠시 뒤 답장한다.
 */
export const openMockChatTransport: ChatTransportFactory = (url, handlers) => {
  let open = true;
  let member: Member | null = null;
  let replyCount = 0;
  const timers = new Set<ReturnType<typeof setTimeout>>();

  const later = (delayMs: number, action: () => void) => {
    const timer = setTimeout(() => {
      timers.delete(timer);

      if (open) {
        action();
      }
    }, delayMs);

    timers.add(timer);
  };

  const close = () => {
    if (!open) {
      return;
    }

    open = false;

    for (const timer of timers) {
      clearTimeout(timer);
    }

    timers.clear();
    setTimeout(handlers.onClose, 0);
  };

  const push = (frame: ChatServerFrameDto) => {
    later(FRAME_DELAY_MS, () => {
      handlers.onText(JSON.stringify(frame));
    });
  };

  const toError = (code: string, clientMessageId: string | null, message: string): ChatServerFrameDto => {
    return { type: "ERROR", roomId: member?.roomId ?? null, clientMessageId, code, message, retryable: code === "C014" };
  };

  /** 오류를 보낸 뒤 연결을 닫는다(인증 실패·권한 상실). */
  const reject = (code: string, message: string) => {
    later(FRAME_DELAY_MS, () => {
      handlers.onText(JSON.stringify(toError(code, null, message)));
      close();
    });
  };

  const authenticate = (frame: unknown) => {
    const ticket = isRecord(frame) && frame.type === "AUTH" && typeof frame.ticket === "string" ? frame.ticket : null;

    if (ticket === null) {
      reject("A001", "첫 프레임은 ticket 을 담은 AUTH 여야 합니다.");
      return;
    }

    const owner = mockDb.chatTickets.get(ticket);
    mockDb.chatTickets.delete(ticket);

    if (owner === undefined) {
      reject("A003", "티켓이 없거나 만료·사용되었습니다.");
      return;
    }

    const problem = findAccessProblem(owner);

    if (problem !== null) {
      reject(problem, "여행방에 접근할 수 없습니다.");
      return;
    }

    member = owner;
    push({ type: "READY", roomId: owner.roomId, userId: owner.userId, latestMessageId: getLatestMessageId(owner.roomId) });
  };

  const replyFromCompanion = ({ userId, roomId }: Member) => {
    const companion = mockDb.rooms.get(roomId)?.members.find((roomMember) => {
      return roomMember.userId !== userId;
    });

    if (companion === undefined) {
      return;
    }

    later(REPLY_DELAY_MS, () => {
      const body = MOCK_CHAT_REPLIES[replyCount % MOCK_CHAT_REPLIES.length];
      replyCount += 1;
      const message = saveMessage(roomId, { id: companion.userId, nickname: companion.nickname }, body, `mock-reply-${Date.now()}-${replyCount}`);
      handlers.onText(JSON.stringify({ type: "MESSAGE_CREATED", roomId, message } satisfies ChatServerFrameDto));
    });
  };

  const sendMessage = (sender: Member, frame: Record<string, unknown>) => {
    const { roomId, clientMessageId, body } = frame;
    const echoId = typeof clientMessageId === "string" ? clientMessageId : null;

    if (roomId == null || clientMessageId == null || body == null) {
      push(toError("C001", echoId, "roomId·clientMessageId·body는 필수입니다."));
      return;
    }

    if (typeof roomId !== "number" || !Number.isSafeInteger(roomId) || typeof clientMessageId !== "string" || typeof body !== "string") {
      push(toError("C002", echoId, "프레임 필드 형식이 올바르지 않습니다."));
      return;
    }

    if (roomId !== sender.roomId) {
      push(toError("C003", echoId, "roomId 가 연결된 여행방과 다릅니다."));
      return;
    }

    const problem = findAccessProblem(sender);

    if (problem !== null) {
      reject(problem, "여행방에 접근할 수 없습니다.");
      return;
    }

    const clientMessageIdLength = countCodePoints(clientMessageId);

    if (clientMessageIdLength === 0 || clientMessageIdLength > CLIENT_MESSAGE_ID_MAX_LENGTH) {
      push(toError("C003", echoId, "clientMessageId: 1~100자여야 합니다."));
      return;
    }

    if (body.trim() === "" || countCodePoints(body) > BODY_MAX_LENGTH) {
      push(toError("C003", echoId, "body: 빈 본문이거나 최대 글자 수를 넘었습니다."));
      return;
    }

    const saved = (mockDb.roomMessages.get(roomId) ?? []).find((message) => {
      return message.sender.id === sender.userId && message.clientMessageId === clientMessageId;
    });

    if (saved !== undefined && saved.body !== body) {
      push(toError("C005", echoId, "같은 메시지 ID에 다른 본문을 사용할 수 없습니다."));
      return;
    }

    if (saved !== undefined) {
      // 같은 clientMessageId·같은 본문은 새로 저장하지 않고 원래 메시지를 다시 알려 준다.
      push({ type: "MESSAGE_ACK", roomId, clientMessageId, replayed: true, message: saved });
      return;
    }

    const nickname = mockDb.rooms.get(roomId)?.members.find((roomMember) => {
      return roomMember.userId === sender.userId;
    })?.nickname ?? "";

    const message = saveMessage(roomId, { id: sender.userId, nickname }, body, clientMessageId);
    push({ type: "MESSAGE_ACK", roomId, clientMessageId, replayed: false, message });
    push({ type: "MESSAGE_CREATED", roomId, message });
    replyFromCompanion(sender);
  };

  const ping = (sender: Member) => {
    const problem = findAccessProblem(sender);

    if (problem !== null) {
      reject(problem, "여행방에 접근할 수 없습니다.");
      return;
    }

    push({ type: "PONG", roomId: sender.roomId, latestMessageId: getLatestMessageId(sender.roomId) });
  };

  if (url.endsWith(MOCK_CHAT_CONNECT_PATH)) {
    later(OPEN_DELAY_MS, handlers.onOpen);
  } else {
    later(OPEN_DELAY_MS, close);
  }

  return {
    send: (text) => {
      if (!open) {
        return;
      }

      const frame = parseFrame(text);

      if (member === null) {
        authenticate(frame);
        return;
      }

      if (!isRecord(frame)) {
        push(toError("C002", null, "JSON 객체 프레임이어야 합니다."));
        return;
      }

      if (frame.type === "SEND_MESSAGE") {
        sendMessage(member, frame);
      } else if (frame.type === "PING") {
        ping(member);
      } else {
        push(toError("C003", null, "지원하지 않는 type 입니다."));
      }
    },

    close,
  };
};
