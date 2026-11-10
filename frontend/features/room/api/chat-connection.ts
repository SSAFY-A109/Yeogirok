/**
 * 실시간 연결 상태. `READY`일 때만 메시지를 보낼 수 있다.
 * `UNAVAILABLE`은 실시간 연결을 아직 붙이지 않은 상태다(1단계).
 */
export type ChatConnectionStatus = "UNAVAILABLE" | "CONNECTING" | "READY" | "RECONNECTING" | "CLOSED";

export type ChatConnection = {
  status: ChatConnectionStatus;
  /** 본문을 그대로 보낸다. `status`가 `READY`가 아니면 화면이 부르지 않는다. */
  send: (body: string) => void;
};

const UNAVAILABLE_CONNECTION: ChatConnection = {
  status: "UNAVAILABLE",

  send: () => {},
};

/**
 * 여행방 채팅의 실시간 연결(API-077 티켓 → WebSocket `/ws/v1`).
 * 1단계에서는 연결하지 않고 항상 `UNAVAILABLE`을 돌려준다. 다음 단계에서 이 훅의 구현만 바꾼다.
 */
export function useChatConnection(roomId: number): ChatConnection {
  return UNAVAILABLE_CONNECTION;
}
