import { createIdempotencyKey } from "@/api/idempotency";
import { getReconnectDelay, getRoomAccessErrorCode } from "@/features/room/api/chat-session";
import { openChatSocket, type ChatSocket } from "@/features/room/api/chat-socket";
import { issueChatTicket } from "@/features/room/api/chat.api";
import type { RoomChange } from "@/features/room/chat-model";
import { ROOM_ACCESS_ERROR_CODES } from "@/features/room/errors";

export type RoomUpdateSessionHandlers = {
  /** 연결이 준비될 때마다 부른다. 처음 연결과 다시 연결을 구분하지 않는다. */
  onReady: () => void;
  onRoomUpdated: (changes: RoomChange[]) => void;
  /** 여행방이 없어졌거나 참여자가 아니게 되어 연결을 끝냈을 때 부른다. 그 뒤로는 `start`해도 연결하지 않는다. */
  onAccessLost: () => void;
};

export type RoomUpdateSession = {
  /** 연결을 시작한다. 이미 연결 중이면 아무 일도 하지 않는다. */
  start: () => void;
  stop: () => void;
};

const ignoreChatFrame = () => {};

/**
 * 여행방 변경 알림(`ROOM_UPDATED`)만 받는 실시간 연결. 채팅과 같은 `/ws/v1`에 티켓(API-077)으로 붙고, 끊기면 다시 연결한다.
 * 채팅 메시지는 읽지 않는다. 서버는 알림 전달을 보장하지 않으므로, 쓰는 쪽이 `onReady`에서 놓친 변경을 다시 조회한다.
 */
export function createRoomUpdateSession(roomId: number, handlers: RoomUpdateSessionHandlers): RoomUpdateSession {
  let running = false;
  let accessLost = false;
  /** 연결을 새로 시작하거나 끝낼 때마다 늘린다. 그 전에 시작한 티켓 요청의 응답을 버리는 데 쓴다. */
  let connectionSeq = 0;
  let socket: ChatSocket | null = null;
  let reconnectAttempt = 0;
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;

  const disconnect = () => {
    connectionSeq += 1;
    clearTimeout(reconnectTimer);
    socket?.close();
    socket = null;
  };

  const endForLostAccess = () => {
    accessLost = true;
    running = false;
    disconnect();
    handlers.onAccessLost();
  };

  const scheduleReconnect = () => {
    reconnectTimer = setTimeout(() => {
      void connect();
    }, getReconnectDelay(reconnectAttempt));

    reconnectAttempt += 1;
  };

  const connect = async () => {
    connectionSeq += 1;
    const seq = connectionSeq;

    try {
      // 티켓은 한 번만 쓸 수 있어 연결할 때마다 새 멱등키로 새로 받는다.
      const ticket = await issueChatTicket({ roomId, idempotencyKey: createIdempotencyKey() });

      if (seq !== connectionSeq) {
        return;
      }

      socket = openChatSocket(roomId, ticket, {
        onReady: () => {
          reconnectAttempt = 0;
          handlers.onReady();
        },

        onMessage: ignoreChatFrame,
        onAck: ignoreChatFrame,
        onPong: ignoreChatFrame,
        onRoomUpdated: handlers.onRoomUpdated,

        onError: ({ code }) => {
          if (ROOM_ACCESS_ERROR_CODES.includes(code)) {
            endForLostAccess();
          }
        },

        onClose: () => {
          socket = null;
          scheduleReconnect();
        },
      });
    } catch (error) {
      if (seq !== connectionSeq) {
        return;
      }

      if (getRoomAccessErrorCode(error) === null) {
        scheduleReconnect();
      } else {
        endForLostAccess();
      }
    }
  };

  return {
    start: () => {
      if (running || accessLost) {
        return;
      }

      running = true;
      reconnectAttempt = 0;
      void connect();
    },

    stop: () => {
      running = false;
      disconnect();
    },
  };
}
