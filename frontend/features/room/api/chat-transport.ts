export type ChatTransportHandlers = {
  onOpen: () => void;
  onText: (text: string) => void;
  /** 연결이 닫히면 부른다. 앱이 `close`로 닫았을 때도 부른다. */
  onClose: () => void;
};

/** 채팅 연결이 WebSocket에서 쓰는 부분. */
export type ChatTransport = {
  send: (text: string) => void;
  close: () => void;
};

export type ChatTransportFactory = (url: string, handlers: ChatTransportHandlers) => ChatTransport;

function openWebSocket(url: string, handlers: ChatTransportHandlers): ChatTransport {
  const socket = new WebSocket(url);

  socket.onopen = () => {
    handlers.onOpen();
  };

  socket.onmessage = (event) => {
    if (typeof event.data === "string") {
      handlers.onText(event.data);
    }
  };

  socket.onclose = () => {
    handlers.onClose();
  };

  // 연결 실패는 뒤따르는 close 이벤트가 알린다. 열려 있는 채로 남지 않게 닫기만 한다.
  socket.onerror = () => {
    socket.close();
  };

  return {
    send: (text) => {
      socket.send(text);
    },

    close: () => {
      socket.close();
    },
  };
}

let openTransport: ChatTransportFactory = openWebSocket;

/**
 * WebSocket 대신 쓸 연결을 등록한다. 개발 환경의 목 서버만 부른다(`api/mocks/start.ts`).
 * MSW 2는 WebSocket을 가로채지 못해, 목은 요청을 가로채는 대신 연결을 바꿔 끼운다.
 */
export function setChatTransportFactory(factory: ChatTransportFactory): void {
  openTransport = factory;
}

export function openChatTransport(url: string, handlers: ChatTransportHandlers): ChatTransport {
  return openTransport(url, handlers);
}
