import { api, idempotentRequestConfig, TIMEOUT_MUTATION_MS } from "@/api/client";
import {
  toChatMessagePage,
  toChatReadState,
  toChatReplayPage,
  toChatTicket,
  type ChatMessagePage,
  type ChatMessagePageDto,
  type ChatReadRequestDto,
  type ChatReadResponseDto,
  type ChatReadState,
  type ChatReplayPage,
  type ChatTicket,
  type ChatTicketRequestDto,
  type ChatTicketResponseDto,
} from "@/features/room/chat-model";

export type GetChatMessagesInput = {
  roomId: number;
  /** 이 메시지보다 과거의 이력을 받는다. 없으면 가장 최근 이력이다. */
  beforeMessageId?: number;
  /** 한 쪽의 메시지 수. 서버 허용 범위는 1~50이고 벗어나면 400 C007이다. */
  limit: number;
};

/** API-076 여행방 채팅 이력. 조회만으로는 읽은 위치가 바뀌지 않는다. */
export async function getChatMessages({ roomId, beforeMessageId, limit }: GetChatMessagesInput, signal?: AbortSignal): Promise<ChatMessagePage> {
  const { data } = await api.get<ChatMessagePageDto>(`/rooms/${roomId}/messages`, { params: { beforeMessageId, limit }, signal });
  return toChatMessagePage(data);
}

export type GetChatMessagesAfterInput = {
  roomId: number;
  /** 이 메시지 뒤에 저장된 메시지를 받는다. 받은 메시지가 없으면 0이다. */
  afterMessageId: number;
  /** 첫 쪽 응답이 고정한 복구 상한. 첫 쪽에는 보내지 않고, 다음 쪽부터 같은 값을 보낸다. */
  throughMessageId?: number;
  /** 한 쪽의 메시지 수. 서버 허용 범위는 1~50이다. */
  limit: number;
};

/** API-076 재연결 복구 조회. `afterMessageId` 뒤부터 복구 상한까지를 오래된 순으로 받는다. */
export async function getChatMessagesAfter(
  { roomId, afterMessageId, throughMessageId, limit }: GetChatMessagesAfterInput,
  signal?: AbortSignal,
): Promise<ChatReplayPage> {
  const { data } = await api.get<ChatMessagePageDto>(`/rooms/${roomId}/messages`, { params: { afterMessageId, throughMessageId, limit }, signal });
  return toChatReplayPage(data);
}

export type MarkChatReadInput = { roomId: number; lastReadMessageId: number };

/** API-088 읽은 위치 갱신. 화면에서 실제로 본 메시지만 보낸다. 서버는 더 작은 값을 받으면 기존 위치를 유지한다. */
export async function markChatRead({ roomId, lastReadMessageId }: MarkChatReadInput): Promise<ChatReadState> {
  const request: ChatReadRequestDto = { lastReadMessageId };
  const { data } = await api.patch<ChatReadResponseDto>(`/rooms/${roomId}/messages/read`, request, { timeout: TIMEOUT_MUTATION_MS });
  return toChatReadState(data);
}

export type IssueChatTicketInput = {
  roomId: number;
  /** 연결을 시도할 때마다 새로 만든다. 티켓은 한 번만 쓸 수 있어 같은 키로 받은 티켓은 다시 쓸 수 없다. */
  idempotencyKey: string;
};

/** API-077 실시간 연결 티켓 발급. 받은 티켓은 바로 WebSocket 첫 프레임(`AUTH`)에 담아 쓴다. */
export async function issueChatTicket({ roomId, idempotencyKey }: IssueChatTicketInput, signal?: AbortSignal): Promise<ChatTicket> {
  const request: ChatTicketRequestDto = { roomId };
  const { data } = await api.post<ChatTicketResponseDto>("/realtime/tickets", request, { ...idempotentRequestConfig(idempotencyKey), signal });
  return toChatTicket(data);
}
