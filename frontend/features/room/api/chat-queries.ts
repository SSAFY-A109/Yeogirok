import { infiniteQueryOptions, useInfiniteQuery, useMutation, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { useCallback, useRef } from "react";

import { isApiError } from "@/api/errors";
import { getChatMessages, markChatRead } from "@/features/room/api/chat.api";
import { roomKeys, roomOptions, roomsOptions } from "@/features/room/api/queries";
import type { ChatReadState } from "@/features/room/chat-model";

/** API-076 한 쪽의 메시지 수. 서버 허용 범위는 1~50이다. */
export const CHAT_PAGE_SIZE = 20;

export const chatKeys = {
  messages: (roomId: number) => {
    return [...roomKeys.all, "messages", roomId] as const;
  },
};

/**
 * 채팅 이력(API-076). 첫 쪽이 가장 최근 대화이고, 다음 쪽은 앞 쪽의 가장 오래된 메시지보다 과거다.
 * 쪽 안의 메시지는 오래된 → 최신 순이다.
 */
export function chatMessagesOptions(roomId: number) {
  return infiniteQueryOptions({
    queryKey: chatKeys.messages(roomId),

    queryFn: ({ pageParam, signal }) => {
      return getChatMessages({ roomId, beforeMessageId: pageParam ?? undefined, limit: CHAT_PAGE_SIZE }, signal);
    },

    initialPageParam: null as number | null,

    getNextPageParam: (oldestPage) => {
      return oldestPage.hasMore && oldestPage.messages.length > 0 ? oldestPage.messages[0].id : null;
    },
  });
}

export function useChatMessages(roomId: number) {
  return useInfiniteQuery(chatMessagesOptions(roomId));
}

/** API-088 응답을 이력의 읽은 위치와 여행방 상세·목록의 안 읽음 표시에 반영한다. */
function storeChatReadState(queryClient: QueryClient, roomId: number, readState: ChatReadState): void {
  queryClient.setQueryData(chatMessagesOptions(roomId).queryKey, (history) => {
    if (history === undefined || history.pages.length === 0) {
      return history;
    }

    const [latestPage, ...olderPages] = history.pages;
    return { ...history, pages: [{ ...latestPage, readState }, ...olderPages] };
  });

  queryClient.setQueryData(roomOptions(roomId).queryKey, (room) => {
    return room === undefined ? room : { ...room, hasUnreadChat: readState.hasUnread };
  });

  queryClient.setQueryData(roomsOptions().queryKey, (rooms) => {
    return rooms?.map((room) => {
      return room.id === roomId ? { ...room, hasUnreadChat: readState.hasUnread } : room;
    });
  });
}

/** 읽은 위치 갱신(API-088). 화면이 닫힌 뒤에 응답이 와도 안 읽음 표시를 맞춘다. */
export function useMarkChatRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: markChatRead,
    retry: false,

    onSuccess: (readState, { roomId }) => {
      storeChatReadState(queryClient, roomId, readState);
    },
  });
}

/**
 * 화면에 보인 메시지를 알리면 읽은 위치를 서버에 보낸다. 반환 함수는 렌더링마다 같다.
 * 이미 읽은 위치 이하의 메시지는 보내지 않고, 요청은 한 번에 하나만 보낸다. 요청 중에 더 최신 메시지를 봤으면 끝난 뒤 그 위치를 보낸다.
 * 다시 보내도 풀리지 않는 오류(4xx)를 받으면 이 화면에서는 더 보내지 않는다.
 */
export function useChatReadReporter(roomId: number): (messageId: number) => void {
  const queryClient = useQueryClient();
  const { mutate } = useMarkChatRead();
  const progress = useRef({ seenId: 0, sentId: 0, sending: false, stopped: false });

  return useCallback((messageId: number) => {
    const state = progress.current;
    state.seenId = Math.max(state.seenId, messageId);

    const sendLatest = () => {
      const history = queryClient.getQueryData(chatMessagesOptions(roomId).queryKey);
      const readId = Math.max(history?.pages[0]?.readState.lastReadMessageId ?? 0, state.sentId);

      if (state.sending || state.stopped || state.seenId <= readId) {
        return;
      }

      const lastReadMessageId = state.seenId;
      state.sending = true;

      mutate({ roomId, lastReadMessageId }, {
        onSuccess: () => {
          state.sentId = lastReadMessageId;
          state.sending = false;
          sendLatest();
        },

        onError: (error) => {
          state.sending = false;
          state.stopped = isApiError(error) && error.status >= 400 && error.status < 500;
        },
      });
    };

    sendLatest();
  }, [mutate, queryClient, roomId]);
}
