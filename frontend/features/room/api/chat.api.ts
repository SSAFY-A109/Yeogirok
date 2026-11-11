import { queryOptions } from "@tanstack/react-query";

import { api, idempotentRequestConfig, TIMEOUT_MUTATION_MS } from "@/api/client";
import { isApiError } from "@/api/errors";
import { createIdempotencyKey } from "@/api/idempotency";
import type { ChatHistoryDto } from "../chat-model";
import type { ChatReadStateDto } from "../model";

type HistoryCursor =
  | { beforeMessageId?: number; afterMessageId?: never; throughMessageId?: never }
  | { beforeMessageId?: never; afterMessageId: number; throughMessageId?: number };

type ChatTicketDto = {
  ticket: string;
  expiresAt: string;
  connectPath: "/ws/v1";
  roomId: number;
};

export function isChatAccessError(error: unknown): boolean {
  return isApiError(error) && (error.code === "RO001" || error.code === "RO002");
}

export async function getChatHistory(roomId: number, params: HistoryCursor, signal?: AbortSignal) {
  const response = await api.get<ChatHistoryDto>(`/rooms/${roomId}/messages`, { params, signal });
  return response.data;
}

export function chatHistoryOptions(roomId: number) {
  return queryOptions({
    queryKey: ["room", "chat", roomId] as const,

    queryFn: ({ signal }) => {
      return getChatHistory(roomId, {}, signal);
    },

    staleTime: 0,
    retry: false,
  });
}

export async function createChatTicket(roomId: number, signal: AbortSignal) {
  const response = await api.post<ChatTicketDto>("/realtime/tickets", { roomId }, {
    ...idempotentRequestConfig(createIdempotencyKey()),
    signal,
  });
  return response.data;
}

export async function updateChatReadPosition(roomId: number, lastReadMessageId: number, signal: AbortSignal) {
  const response = await api.patch<ChatReadStateDto & { roomId: number }>(
    `/rooms/${roomId}/messages/read`, { lastReadMessageId }, { signal, timeout: TIMEOUT_MUTATION_MS },
  );
  return response.data;
}
