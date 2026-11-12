import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, useSyncExternalStore } from "react";
import { AppState } from "react-native";

import { chatMessagesOptions } from "@/features/room/api/chat-queries";
import { createChatSession, type ChatSession, type ChatSessionState } from "@/features/room/api/chat-session";
import { roomKeys } from "@/features/room/api/queries";
import { useRoomChangeRefresher } from "@/features/room/api/room-updates";

export type ChatConnection = ChatSessionState & Pick<ChatSession, "send" | "retry" | "discard">;

/**
 * 여행방 채팅의 실시간 연결(API-077 티켓 → WebSocket `/ws/v1`). 화면이 떠 있는 동안 연결하고, 앱이 백그라운드로 가면 닫았다가 돌아오면 다시 연결한다.
 *
 * 이 연결에서 받은 메시지(`liveMessages`)는 이력 캐시에 넣지 않고 따로 돌려준다. 이력 캐시는 다시 받을 때 서버 응답으로 통째로 바뀌므로,
 * 화면이 이력과 `liveMessages`를 메시지 id로 합쳐 보여 준다(`mergeChatMessages`).
 *
 * 같은 연결로 오는 여행방 변경 알림(`ROOM_UPDATED`)을 받으면 여행방을 다시 받는다. 채팅 화면이 떠 있는 동안에는 아래 여행방 화면의 연결이 닫혀 있다.
 *
 * @param enabled 채팅 이력을 받은 뒤에 `true`로 준다. 받아 둔 이력의 가장 최신 메시지 뒤부터 빠진 메시지를 복구한다.
 */
export function useChatConnection(roomId: number, enabled: boolean): ChatConnection {
  const queryClient = useQueryClient();
  const refreshRoom = useRoomChangeRefresher(roomId);

  const [session] = useState(() => {
    return createChatSession(roomId, () => {
      void queryClient.invalidateQueries({ queryKey: roomKeys.list() });
    }, refreshRoom);
  });

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const latestPageMessages = queryClient.getQueryData(chatMessagesOptions(roomId).queryKey)?.pages[0]?.messages ?? [];
    session.start(latestPageMessages[latestPageMessages.length - 1]?.id ?? 0);

    const appStateSubscription = AppState.addEventListener("change", (appState) => {
      if (appState === "active") {
        session.resume();
      } else if (appState === "background") {
        session.suspend();
      }
    });

    return () => {
      appStateSubscription.remove();
      session.stop();
    };
  }, [enabled, queryClient, roomId, session]);

  const state = useSyncExternalStore(session.subscribe, session.getState);

  return { ...state, send: session.send, retry: session.retry, discard: session.discard };
}
