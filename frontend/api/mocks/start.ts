import "./polyfills";

import { setChatTransportFactory } from "../../features/room/api/chat-transport";

import { openMockChatTransport } from "./chat-transport";
import { server } from "./native";
import { onUnhandledRequest } from "./on-unhandled-request";

// 네이티브는 번들 실행 직후 "main"을 실행하므로 서버를 동기로 시작한다.
// Promise를 반환하는 쪽은 Worker 준비를 기다려야 하는 웹(start.web.ts)뿐이다.
export function startMocking(): Promise<void> | void {
  server.listen({ onUnhandledRequest });
  // MSW 2는 WebSocket을 가로채지 못해 채팅 연결(`/ws/v1`)은 목 연결로 바꿔 끼운다.
  setChatTransportFactory(openMockChatTransport);
}
