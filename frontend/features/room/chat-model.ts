import { assertId, ContractMismatchError } from "../../lib/contract";
import { formatServerDateTime, parseServerDateTime, type LocalDate } from "../../lib/date";
import type { ChatReadStateDto } from "./model";

// 여행방 채팅(API-076·088) DTO는 backend PR #82(`backend/feat/S15P31A109-162-chat-api`, 미머지, 2026-11-10)의
// ChatController·ChatMessagePage·ChatReadResponse와 대조했다. 머지되면 Swagger로 다시 맞춘다.

/** 메시지 본문 최대 글자 수(코드 포인트 기준). 넘으면 서버가 C003으로 거절한다. */
export const CHAT_BODY_MAX_LENGTH = 100;

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
    clientMessageId: dto.clientMessageId,
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

/** 이력의 쪽들(가장 최근 쪽이 맨 앞)을 오래된 → 최신 순의 한 목록으로 잇는다. */
export function joinChatPages(pages: readonly ChatMessagePage[]): ChatMessage[] {
  return [...pages].reverse().flatMap((page) => {
    return page.messages;
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
