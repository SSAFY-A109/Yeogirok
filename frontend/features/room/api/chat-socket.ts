import { REALTIME_BASE_URL } from "@/api/client";
import { openChatTransport } from "@/features/room/api/chat-transport";
import { parseChatServerFrame, type ChatClientFrameDto, type ChatMessage, type ChatServerFrame, type ChatTicket } from "@/features/room/chat-model";

/** 앱이 PING을 보내는 간격. 서버는 60초 동안 프레임이 없으면 연결을 닫는다(backend `docs/07-chat.md`). */
const PING_INTERVAL_MS = 25_000;

/** PING을 보낸 뒤 PONG을 기다리는 시간. 넘기면 끊긴 연결로 보고 닫는다. */
const PONG_TIMEOUT_MS = 10_000;

/** 연결을 연 뒤 READY를 기다리는 시간. 서버의 AUTH 대기(10초)보다 길게 둔다. */
const READY_TIMEOUT_MS = 15_000;

/** 서버가 보낸 `ERROR` 프레임. `clientMessageId`가 있으면 그 메시지의 전송 실패다. */
export type ChatSocketError = { code: string; clientMessageId: string | null; retryable: boolean };

export type ChatSocketHandlers = {
  onReady: (ready: { userId: number; latestMessageId: number | null }) => void;
  /** 방에 새로 저장된 메시지. 내가 보낸 메시지도 올 수 있다. */
  onMessage: (message: ChatMessage) => void;
  /** 내가 보낸 메시지가 저장됐다. */
  onAck: (message: ChatMessage) => void;
  /** 서버에 저장된 가장 최신 메시지 id. 방에 메시지가 없으면 `null`. */
  onPong: (latestMessageId: number | null) => void;
  onError: (error: ChatSocketError) => void;
  /** 서버나 네트워크가 연결을 닫았을 때 한 번 부른다. `close()`로 직접 닫으면 부르지 않는다. */
  onClose: () => void;
};

export type ChatSocket = {
  /** `onReady` 뒤에만 부른다. 같은 `clientMessageId`·같은 본문은 다시 보내도 한 번만 저장된다. */
  sendMessage: (clientMessageId: string, body: string) => void;
  close: () => void;
};

/**
 * 채팅 WebSocket 연결 하나(`/ws/v1`). 열리면 티켓으로 인증하고(`AUTH` → `READY`), 그 뒤로 PING을 보내 연결을 유지한다.
 * 티켓은 한 번만 쓸 수 있어 다시 연결할 때는 새 티켓으로 새 연결을 만든다.
 */
export function openChatSocket(roomId: number, ticket: ChatTicket, handlers: ChatSocketHandlers): ChatSocket {
  let closed = false;
  let pingTimer: ReturnType<typeof setInterval> | undefined;
  let replyTimer: ReturnType<typeof setTimeout> | undefined;

  const send = (frame: ChatClientFrameDto) => {
    transport.send(JSON.stringify(frame));
  };

  const shutDown = () => {
    closed = true;
    clearInterval(pingTimer);
    clearTimeout(replyTimer);
    transport.close();
  };

  const closeUnexpectedly = () => {
    if (closed) {
      return;
    }

    shutDown();
    handlers.onClose();
  };

  const expectReplyWithin = (timeoutMs: number) => {
    clearTimeout(replyTimer);
    replyTimer = setTimeout(closeUnexpectedly, timeoutMs);
  };

  const ping = () => {
    send({ type: "PING" });
    expectReplyWithin(PONG_TIMEOUT_MS);
  };

  const handleFrame = (frame: ChatServerFrame) => {
    switch (frame.type) {
      case "READY":
        clearTimeout(replyTimer);
        clearInterval(pingTimer);
        pingTimer = setInterval(ping, PING_INTERVAL_MS);
        handlers.onReady({ userId: frame.userId, latestMessageId: frame.latestMessageId });
        return;

      case "PONG":
        clearTimeout(replyTimer);
        handlers.onPong(frame.latestMessageId);
        return;

      case "MESSAGE_ACK":
        handlers.onAck(frame.message);
        return;

      case "MESSAGE_CREATED":
        handlers.onMessage(frame.message);
        return;

      case "ERROR":
        handlers.onError({ code: frame.code, clientMessageId: frame.clientMessageId, retryable: frame.retryable });
        return;

      case "UNKNOWN":
        return;
    }
  };

  const transport = openChatTransport(`${REALTIME_BASE_URL}${ticket.connectPath}`, {
    onOpen: () => {
      if (!closed) {
        send({ type: "AUTH", ticket: ticket.ticket });
      }
    },

    onText: (text) => {
      if (closed) {
        return;
      }

      let frame: ChatServerFrame;

      try {
        frame = parseChatServerFrame(text);
      } catch {
        // 계약과 다른 프레임을 보낸 연결은 믿을 수 없어 닫는다. 다시 연결한 뒤 빠진 메시지는 API-076 복구 조회로 받는다.
        closeUnexpectedly();
        return;
      }

      handleFrame(frame);
    },

    onClose: closeUnexpectedly,
  });

  expectReplyWithin(READY_TIMEOUT_MS);

  return {
    sendMessage: (clientMessageId, body) => {
      if (!closed) {
        send({ type: "SEND_MESSAGE", roomId, clientMessageId, body });
      }
    },

    close: () => {
      if (!closed) {
        shutDown();
      }
    },
  };
}
