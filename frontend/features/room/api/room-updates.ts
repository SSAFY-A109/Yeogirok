import { useQueryClient } from "@tanstack/react-query";
import { useFocusEffect } from "expo-router";
import { useCallback, useEffect, useRef } from "react";
import { AppState } from "react-native";

import { refreshRoomAfterChanges } from "@/features/room/api/queries";
import { createRoomUpdateSession } from "@/features/room/api/room-update-session";
import { ROOM_CHANGES, type RoomChange } from "@/features/room/chat-model";

/** 변경 알림을 모으는 시간. 짧은 사이에 여러 개가 와도 이 시간 뒤에 한 번만 다시 받는다. */
const ROOM_REFRESH_DELAY_MS = 300;

/**
 * 여행방 변경 알림(`ROOM_UPDATED`)을 모아 다시 받는 함수를 돌려준다. 반환 함수는 렌더링마다 같다.
 * 첫 알림부터 `ROOM_REFRESH_DELAY_MS` 동안 온 알림의 바뀐 부분을 합쳐 한 번에 다시 받는다.
 * 알림은 도착 순서가 바뀔 수 있어 `version`으로 거르지 않고, 내가 바꾼 것도 다시 받는다(다른 기기에서 바꿨을 수 있다).
 */
export function useRoomChangeRefresher(roomId: number): (changes: readonly RoomChange[]) => void {
  const queryClient = useQueryClient();
  const waiting = useRef<{ changes: Set<RoomChange>; timer: ReturnType<typeof setTimeout> | undefined }>({ changes: new Set(), timer: undefined });

  useEffect(() => {
    const state = waiting.current;

    return () => {
      clearTimeout(state.timer);
      state.timer = undefined;
      state.changes.clear();
    };
  }, []);

  return useCallback((changes: readonly RoomChange[]) => {
    const state = waiting.current;

    for (const change of changes) {
      state.changes.add(change);
    }

    state.timer ??= setTimeout(() => {
      const collected = new Set(state.changes);
      state.changes.clear();
      state.timer = undefined;
      refreshRoomAfterChanges(queryClient, roomId, collected);
    }, ROOM_REFRESH_DELAY_MS);
  }, [queryClient, roomId]);
}

/**
 * 여행방 화면이 보이는 동안 실시간 연결(`/ws/v1`)을 열어, 다른 일행이 바꾼 내용을 바로 다시 받는다.
 * 화면을 벗어나거나 앱이 백그라운드로 가면 닫는다. 그 위에 채팅 화면이 뜨면 채팅 연결이 같은 알림을 받는다.
 * 서버는 알림 전달을 보장하지 않는다. 그래서 다시 연결됐을 때(끊겼다 붙음·앱으로 돌아옴·화면에 다시 들어옴)는 놓친 변경이 있다고 보고 전부 다시 받는다.
 * 처음 연결됐을 때는 화면이 방금 받은 내용이라 다시 받지 않는다.
 */
export function useRoomUpdates(roomId: number): void {
  const refresh = useRoomChangeRefresher(roomId);
  const hasConnected = useRef(false);

  useFocusEffect(useCallback(() => {
    const session = createRoomUpdateSession(roomId, {
      onReady: () => {
        if (hasConnected.current) {
          refresh(ROOM_CHANGES);
        }

        hasConnected.current = true;
      },

      onRoomUpdated: refresh,

      onAccessLost: () => {
        refresh(["MEMBERS"]);
      },
    });

    const followAppState = (appState: typeof AppState.currentState) => {
      if (appState === "active") {
        session.start();
      } else if (appState === "background") {
        session.stop();
      }
    };

    followAppState(AppState.currentState);
    const appStateSubscription = AppState.addEventListener("change", followAppState);

    return () => {
      appStateSubscription.remove();
      session.stop();
    };
  }, [refresh, roomId]));
}
