import type { ChatReadStateDto } from "./model";

export type ChatMessageDto = {
  id: number;
  sender: { id: number | null; nickname: string };
  body: string;
  createdAt: string;
  clientMessageId: string | null;
};

export type ChatHistoryDto = {
  items: ChatMessageDto[];
  hasMore: boolean;
  nextAfterMessageId: number | null;
  replayThroughMessageId: number | null;
  chatReadState: ChatReadStateDto;
};

export function mergeChatMessages(current: ChatMessageDto[], incoming: ChatMessageDto[]) {
  const byId = new Map(current.map((message) => {
    return [message.id, message];
  }));

  for (const message of incoming) {
    byId.set(message.id, message);
  }

  return [...byId.values()].sort((first, second) => {
    return first.id - second.id;
  });
}

export function getSeenReadPosition(
  messages: ChatMessageDto[], seen: ReadonlySet<number>, lastRead: number | null,
  hasOlder: boolean, confirmedThrough: number,
): number | null {
  if (hasOlder && (lastRead === null || lastRead < (messages[0]?.id ?? 0))) {
    return null;
  }

  let position = lastRead;

  for (const { id } of messages) {
    if (lastRead !== null && id <= lastRead) {
      continue;
    }

    if (id > confirmedThrough || !seen.has(id)) {
      break;
    }

    position = id;
  }

  return position === lastRead ? null : position;
}
