import { useFocusEffect } from "expo-router";
import { useCallback, useRef, useState } from "react";
import { AppState } from "react-native";

import { API_BASE_URL } from "@/api/client";
import { createChatTicket, getChatHistory, isChatAccessError } from "./api/chat.api";
import type { ChatMessageDto } from "./chat-model";

type ChatFrame =
  | { type: "READY" | "PONG"; roomId: number; latestMessageId: number | null }
  | { type: "MESSAGE_CREATED"; roomId: number; message: ChatMessageDto }
  | { type: "ERROR"; roomId: number | null; code: string };

function parseFrame(data: unknown): ChatFrame | null {
  if (typeof data !== "string") {
    return null;
  }

  try {
    const frame: ChatFrame | null = JSON.parse(data);
    return frame && typeof frame.type === "string" ? frame : null;
  } catch {
    return null;
  }
}

const CONNECTION_TIMEOUT_MS = 10_000;
const PING_INTERVAL_MS = 20_000;
const MAX_RETRY_DELAY_MS = 30_000;

export function useChatConnection(
  roomId: number, initialCheckpoint: number,
  onMessages: (messages: ChatMessageDto[]) => void, onAccessDenied: () => void,
) {
  const checkpoint = useRef(initialCheckpoint);
  const [confirmedThrough, setConfirmedThrough] = useState(initialCheckpoint);
  const [connected, setConnected] = useState(false);

  useFocusEffect(useCallback(() => {
    // 개발 환경의 이력과 읽음 API는 MSW를 사용한다.
    if (__DEV__) {
      return;
    }

    let attempt: AbortController | undefined;
    let socket: WebSocket | undefined;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let deadline: ReturnType<typeof setTimeout> | undefined;
    let heartbeat: ReturnType<typeof setInterval> | undefined;
    let retries = 0;
    let allowed = true;

    function disconnect() {
      clearTimeout(retryTimer);
      clearTimeout(deadline);
      clearInterval(heartbeat);
      attempt?.abort();
      attempt = undefined;
      socket?.close();
      socket = undefined;
      setConnected(false);
    }

    function handleFailure(accessDenied = false) {
      disconnect();

      if (accessDenied) {
        allowed = false;
        onAccessDenied();
      } else if (AppState.currentState === "active") {
        const delay = Math.min(1000 * 2 ** retries, MAX_RETRY_DELAY_MS);
        retries += 1;
        retryTimer = setTimeout(start, delay * (0.5 + Math.random() / 2));
      }
    }

    async function start() {
      if (attempt || !allowed || AppState.currentState !== "active") {
        return;
      }

      const controller = new AbortController();
      attempt = controller;
      const { signal } = controller;
      let ready = false;
      let recovering = false;
      let buffered: ChatMessageDto[] = [];

      async function recover(latestMessageId: number | null) {
        if (recovering || latestMessageId === null || latestMessageId <= checkpoint.current) {
          return;
        }

        recovering = true;
        let afterMessageId = checkpoint.current;
        let throughMessageId: number | undefined;
        const recovered: ChatMessageDto[] = [];

        try {
          for (;;) {
            const page = await getChatHistory(roomId, { afterMessageId, throughMessageId }, signal);

            if (signal.aborted) {
              return;
            }

            const upper = page.replayThroughMessageId;

            if (upper === null || upper < afterMessageId || (throughMessageId !== undefined && throughMessageId !== upper)) {
              throw new Error("Invalid chat replay boundary");
            }

            throughMessageId = upper;
            recovered.push(...page.items);

            if (!page.hasMore) {
              break;
            }

            const next = page.nextAfterMessageId;

            if (next === null || next <= afterMessageId || next > upper) {
              throw new Error("Invalid chat replay cursor");
            }

            afterMessageId = next;
          }

          checkpoint.current = throughMessageId;
          setConfirmedThrough(throughMessageId);
          onMessages([...recovered, ...buffered]);
          buffered = [];
        } catch (error) {
          if (!signal.aborted) {
            handleFailure(isChatAccessError(error));
          }
        } finally {
          recovering = false;
        }
      }

      try {
        const ticket = await createChatTicket(roomId, signal);

        if (signal.aborted) {
          return;
        }

        if (!API_BASE_URL || ticket.roomId !== roomId || ticket.connectPath !== "/ws/v1" ||
          typeof ticket.ticket !== "string" || !ticket.ticket) {
          throw new Error("Invalid chat ticket");
        }

        const connection = new WebSocket(`${API_BASE_URL.replace(/^https:/, "wss:")}${ticket.connectPath}`);
        socket = connection;
        deadline = setTimeout(handleFailure, CONNECTION_TIMEOUT_MS);

        connection.onopen = () => {
          if (!signal.aborted) {
            connection.send(JSON.stringify({ type: "AUTH", ticket: ticket.ticket }));
          }
        };

        connection.onclose = connection.onerror = () => {
          if (!signal.aborted) {
            handleFailure();
          }
        };

        connection.onmessage = ({ data }) => {
          if (signal.aborted) {
            return;
          }

          const frame = parseFrame(data);

          if (!frame || (frame.roomId !== roomId && !(frame.type === "ERROR" && frame.roomId === null))) {
            return;
          }

          if (frame.type === "ERROR") {
            handleFailure(frame.code === "RO001" || frame.code === "RO002");
            return;
          }

          if (frame.type === "READY" && !ready) {
            ready = true;
            retries = 0;
            clearTimeout(deadline);
            setConnected(true);
            void recover(frame.latestMessageId);

            heartbeat = setInterval(() => {
              connection.send(JSON.stringify({ type: "PING" }));
              deadline = setTimeout(handleFailure, CONNECTION_TIMEOUT_MS);
            }, PING_INTERVAL_MS);
          } else if (ready && frame.type === "PONG") {
            clearTimeout(deadline);
            void recover(frame.latestMessageId);
          } else if (ready && frame.type === "MESSAGE_CREATED" &&
            Number.isSafeInteger(frame.message?.id) && frame.message.id > 0 &&
            typeof frame.message.body === "string" && typeof frame.message.sender?.nickname === "string") {
            if (recovering) {
              buffered.push(frame.message);
            } else {
              onMessages([frame.message]);
              void recover(frame.message.id);
            }
          }
        };
      } catch (error) {
        if (!signal.aborted) {
          handleFailure(isChatAccessError(error));
        }
      }
    }

    const subscription = AppState.addEventListener("change", (state) => {
      if (state === "active") {
        retries = 0;
        void start();
      } else {
        disconnect();
      }
    });

    void start();

    return () => {
      subscription.remove();
      disconnect();
    };
  }, [roomId, onMessages, onAccessDenied]));

  return { connected, confirmedThrough };
}
