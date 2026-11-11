import { setupWorker } from "msw/browser";

import { setChatTransportFactory } from "../../features/room/api/chat-transport";

import { openMockChatTransport } from "./chat-transport";
import { handlers } from "./handlers";
import { onUnhandledRequest } from "./on-unhandled-request";

export async function startMocking() {
  const worker = setupWorker(...handlers);
  await worker.start({ onUnhandledRequest });
  // MSW 2는 WebSocket을 가로채지 못해 채팅 연결(`/ws/v1`)은 목 연결로 바꿔 끼운다.
  setChatTransportFactory(openMockChatTransport);
}
