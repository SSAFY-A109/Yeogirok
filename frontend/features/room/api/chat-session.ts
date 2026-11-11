import { isApiError } from "@/api/errors";
import { createIdempotencyKey } from "@/api/idempotency";
import { openChatSocket, type ChatSocket, type ChatSocketError } from "@/features/room/api/chat-socket";
import { getChatMessagesAfter, issueChatTicket } from "@/features/room/api/chat.api";
import { mergeChatMessages, type ChatMessage, type ChatTicket, type PendingChatMessage } from "@/features/room/chat-model";
import { ROOM_ACCESS_ERROR_CODES } from "@/features/room/errors";

/** 다시 연결하기 전 대기 시간의 시작값. 실패할 때마다 두 배로 늘린다. */
const RECONNECT_BASE_DELAY_MS = 1_000;
const RECONNECT_MAX_DELAY_MS = 30_000;

/** 복구 조회(API-076) 한 쪽의 메시지 수. 서버 허용 최댓값이다. */
const REPLAY_PAGE_SIZE = 50;

/**
 * 실시간 연결 상태. `READY`일 때만 서버가 메시지를 받는다.
 * `CLOSED`는 여행방이 없어졌거나 내가 참여자가 아니게 되어 다시 연결하지 않는 상태다.
 */
export type ChatConnectionStatus = "CONNECTING" | "READY" | "RECONNECTING" | "CLOSED";

export type ChatSessionState = {
  status: ChatConnectionStatus;
  /** `CLOSED`가 된 이유(RO001 여행방 없음, RO002 참여자 아님). 그 밖의 상태에서는 `null`. */
  closedCode: string | null;
  /** 이 연결에서 받은 메시지(실시간 수신·저장 완료·재연결 복구). 오래된 → 최신 순이다. */
  liveMessages: ChatMessage[];
  /** 저장 완료를 받지 못한 내 메시지. 보낸 순서다. */
  pendingMessages: PendingChatMessage[];
};

export type ChatSession = {
  subscribe: (listener: () => void) => () => void;
  getState: () => ChatSessionState;
  /** 연결을 시작한다. `latestKnownMessageId`는 이미 받아 둔 이력의 가장 최신 메시지 id(없으면 0)다. */
  start: (latestKnownMessageId: number) => void;
  stop: () => void;
  /** 앱이 백그라운드로 가면 연결을 닫아 둔다. 보내던 메시지는 `resume` 뒤에 다시 보낸다. */
  suspend: () => void;
  resume: () => void;
  /** 본문을 그대로 보낸다. 연결이 `READY`가 아니면 연결된 뒤에 보낸다. */
  send: (body: string) => void;
  /** `FAILED` 메시지를 같은 `clientMessageId`로 다시 보낸다. */
  retry: (clientMessageId: string) => void;
  /** 보내지 못한 메시지를 목록에서 지운다. */
  discard: (clientMessageId: string) => void;
};

/** 지수 백오프에 무작위 값을 섞은 재연결 대기 시간. 여러 기기가 같은 순간에 다시 붙지 않게 한다. */
function getReconnectDelay(attempt: number): number {
  const ceiling = Math.min(RECONNECT_MAX_DELAY_MS, RECONNECT_BASE_DELAY_MS * 2 ** attempt);
  return ceiling / 2 + Math.random() * (ceiling / 2);
}

function getRoomAccessErrorCode(error: unknown): string | null {
  return isApiError(error) && ROOM_ACCESS_ERROR_CODES.includes(error.code) ? error.code : null;
}

/**
 * 여행방 채팅의 실시간 연결 한 벌. 티켓 발급(API-077) → WebSocket 연결 → 끊기면 다시 연결 → 빠진 메시지 복구(API-076)를 맡는다.
 *
 * - 복구 위치(`checkpoint`)는 "이 id까지는 빠짐없이 받았다"는 뜻이고 읽은 위치와 다르다. 복구 조회가 마지막 쪽까지 성공했을 때만 앞으로 간다.
 * - 실시간 수신은 순서가 바뀌거나 빠질 수 있다. READY·PONG이 알려 준 최신 id가 복구 위치보다 크면 복구 조회로 확인한다.
 * - 저장 완료를 받지 못한 메시지는 다시 연결되면 같은 `clientMessageId`로 다시 보낸다. 서버는 한 번만 저장한다.
 *
 * @param onAccessLost 여행방이 없어졌거나 참여자가 아니게 되어 연결을 끝냈을 때 부른다.
 */
export function createChatSession(roomId: number, onAccessLost: () => void): ChatSession {
  const listeners = new Set<() => void>();
  let state: ChatSessionState = { status: "CONNECTING", closedCode: null, liveMessages: [], pendingMessages: [] };

  let running = false;
  let suspended = false;
  /** 연결을 새로 시작하거나 끝낼 때마다 늘린다. 그 전에 시작한 티켓 요청의 응답을 버리는 데 쓴다. */
  let connectionSeq = 0;
  let socket: ChatSocket | null = null;
  let socketReady = false;
  let myUserId: number | null = null;
  let reconnectAttempt = 0;
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  let checkpoint = 0;
  let replayAbort: AbortController | null = null;

  const setState = (patch: Partial<ChatSessionState>) => {
    state = { ...state, ...patch };

    for (const listener of listeners) {
      listener();
    }
  };

  const disconnect = () => {
    connectionSeq += 1;
    clearTimeout(reconnectTimer);
    socket?.close();
    socket = null;
    socketReady = false;
    replayAbort?.abort();
    replayAbort = null;
  };

  const closeForGood = (code: string) => {
    disconnect();
    setState({ status: "CLOSED", closedCode: code });
    onAccessLost();
  };

  const receive = (messages: readonly ChatMessage[]) => {
    const savedClientIds = new Set(messages.flatMap((message) => {
      return message.senderId === myUserId && message.clientMessageId !== null ? [message.clientMessageId] : [];
    }));

    setState({
      liveMessages: mergeChatMessages(state.liveMessages, messages),

      pendingMessages: state.pendingMessages.filter((pending) => {
        return !savedClientIds.has(pending.clientMessageId);
      }),
    });
  };

  const replay = async () => {
    const abort = new AbortController();
    replayAbort = abort;

    const recovered: ChatMessage[] = [];
    let afterMessageId: number | null = checkpoint;
    let throughMessageId: number | undefined;

    try {
      while (afterMessageId !== null) {
        const page = await getChatMessagesAfter({ roomId, afterMessageId, throughMessageId, limit: REPLAY_PAGE_SIZE }, abort.signal);
        recovered.push(...page.messages);
        throughMessageId = page.throughMessageId;
        afterMessageId = page.nextAfterMessageId;
      }

      if (abort.signal.aborted) {
        return;
      }

      checkpoint = Math.max(checkpoint, throughMessageId ?? checkpoint);
      receive(recovered);
    } catch (error) {
      const accessErrorCode = abort.signal.aborted ? null : getRoomAccessErrorCode(error);

      // 그 밖의 실패는 복구 위치를 그대로 둔다. 다음 PONG·READY에서 같은 위치부터 다시 받는다.
      if (accessErrorCode !== null) {
        closeForGood(accessErrorCode);
      }
    } finally {
      if (replayAbort === abort) {
        replayAbort = null;
      }
    }
  };

  const catchUp = (latestMessageId: number | null) => {
    if (latestMessageId !== null && latestMessageId > checkpoint && replayAbort === null) {
      void replay();
    }
  };

  const markPending = (clientMessageId: string, pendingState: PendingChatMessage["state"]) => {
    setState({
      pendingMessages: state.pendingMessages.map((pending) => {
        return pending.clientMessageId === clientMessageId ? { ...pending, state: pendingState } : pending;
      }),
    });
  };

  const handleSocketError = ({ code, clientMessageId, retryable }: ChatSocketError) => {
    if (ROOM_ACCESS_ERROR_CODES.includes(code)) {
      closeForGood(code);
      return;
    }

    if (clientMessageId !== null) {
      markPending(clientMessageId, retryable ? "FAILED" : "REJECTED");
    }
  };

  const scheduleReconnect = () => {
    reconnectTimer = setTimeout(() => {
      void connect();
    }, getReconnectDelay(reconnectAttempt));

    reconnectAttempt += 1;
  };

  const openSocket = (ticket: ChatTicket) => {
    const opened = openChatSocket(roomId, ticket, {
      onReady: ({ userId, latestMessageId }) => {
        socketReady = true;
        myUserId = userId;
        reconnectAttempt = 0;
        setState({ status: "READY" });

        for (const pending of state.pendingMessages) {
          if (pending.state === "SENDING") {
            opened.sendMessage(pending.clientMessageId, pending.body);
          }
        }

        catchUp(latestMessageId);
      },

      onMessage: (message) => {
        receive([message]);
      },

      onAck: (message) => {
        receive([message]);
      },

      onPong: catchUp,
      onError: handleSocketError,

      onClose: () => {
        socket = null;
        socketReady = false;
        setState({ status: "RECONNECTING" });
        scheduleReconnect();
      },
    });

    socket = opened;
  };

  const connect = async () => {
    connectionSeq += 1;
    const seq = connectionSeq;

    try {
      // 티켓은 한 번만 쓸 수 있어 연결할 때마다 새 멱등키로 새로 받는다.
      const ticket = await issueChatTicket({ roomId, idempotencyKey: createIdempotencyKey() });

      if (seq === connectionSeq) {
        openSocket(ticket);
      }
    } catch (error) {
      if (seq !== connectionSeq) {
        return;
      }

      const accessErrorCode = getRoomAccessErrorCode(error);

      if (accessErrorCode === null) {
        scheduleReconnect();
      } else {
        closeForGood(accessErrorCode);
      }
    }
  };

  return {
    subscribe: (listener) => {
      listeners.add(listener);

      return () => {
        listeners.delete(listener);
      };
    },

    getState: () => {
      return state;
    },

    start: (latestKnownMessageId) => {
      if (running || state.status === "CLOSED") {
        return;
      }

      running = true;
      suspended = false;
      reconnectAttempt = 0;
      checkpoint = Math.max(checkpoint, latestKnownMessageId);

      if (state.status !== "CONNECTING") {
        setState({ status: "CONNECTING" });
      }

      void connect();
    },

    stop: () => {
      running = false;
      disconnect();
    },

    suspend: () => {
      if (!running || suspended || state.status === "CLOSED") {
        return;
      }

      suspended = true;
      disconnect();
      setState({ status: "RECONNECTING" });
    },

    resume: () => {
      if (!running || !suspended) {
        return;
      }

      suspended = false;
      reconnectAttempt = 0;
      void connect();
    },

    send: (body) => {
      const clientMessageId = createIdempotencyKey();
      setState({ pendingMessages: [...state.pendingMessages, { clientMessageId, body, state: "SENDING" }] });

      if (socketReady) {
        socket?.sendMessage(clientMessageId, body);
      }
    },

    retry: (clientMessageId) => {
      const failed = state.pendingMessages.find((pending) => {
        return pending.clientMessageId === clientMessageId && pending.state === "FAILED";
      });

      if (failed === undefined) {
        return;
      }

      markPending(clientMessageId, "SENDING");

      if (socketReady) {
        socket?.sendMessage(clientMessageId, failed.body);
      }
    },

    discard: (clientMessageId) => {
      setState({
        pendingMessages: state.pendingMessages.filter((pending) => {
          return pending.clientMessageId !== clientMessageId;
        }),
      });
    },
  };
}
