import { api, TIMEOUT_MUTATION_MS } from "@/api/client";
import {
  toChatMessagePage,
  toChatReadState,
  type ChatMessagePage,
  type ChatMessagePageDto,
  type ChatReadRequestDto,
  type ChatReadResponseDto,
  type ChatReadState,
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

export type MarkChatReadInput = { roomId: number; lastReadMessageId: number };

/** API-088 읽은 위치 갱신. 화면에서 실제로 본 메시지만 보낸다. 서버는 더 작은 값을 받으면 기존 위치를 유지한다. */
export async function markChatRead({ roomId, lastReadMessageId }: MarkChatReadInput): Promise<ChatReadState> {
  const request: ChatReadRequestDto = { lastReadMessageId };
  const { data } = await api.patch<ChatReadResponseDto>(`/rooms/${roomId}/messages/read`, request, { timeout: TIMEOUT_MUTATION_MS });
  return toChatReadState(data);
}
