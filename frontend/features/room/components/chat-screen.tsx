import { useQuery, useQueryClient } from "@tanstack/react-query";
import { router, useFocusEffect } from "expo-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppState, FlatList, View, type FlatListProps } from "react-native";

import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { chatHistoryOptions, getChatHistory, isChatAccessError, updateChatReadPosition } from "../api/chat.api";
import { roomKeys } from "../api/queries";
import { getSeenReadPosition, mergeChatMessages, type ChatHistoryDto, type ChatMessageDto } from "../chat-model";
import { useChatConnection } from "../use-chat-connection";

const VIEWABILITY_CONFIG = { itemVisiblePercentThreshold: 75, minimumViewTime: 300 };

function goBack() {
  if (router.canGoBack()) {
    router.back();
  } else {
    router.replace("/rooms");
  }
}

export function ChatScreen({ roomId }: { roomId: number | null }) {
  return (
    <Screen>
      <ScreenHeader title="여행방 채팅" onBack={goBack} testID="room-chat.header" />
      {roomId === null || roomId <= 0 ? <ChatUnavailable /> : <ChatHistory roomId={roomId} />}
    </Screen>
  );
}

function ChatUnavailable() {
  return <ChatStatus message="이 여행방의 채팅을 열 수 없어요." />;
}

function ChatStatus({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <View className="flex-1 items-center justify-center gap-3 px-5">
      <Text className="text-center text-body-sm text-foreground-secondary">{message}</Text>
      {onRetry && <Button variant="outline" onPress={onRetry}><Text>다시 시도</Text></Button>}
    </View>
  );
}

function ChatHistory({ roomId }: { roomId: number }) {
  const query = useQuery(chatHistoryOptions(roomId));
  const [denied, setDenied] = useState(false);

  const denyAccess = useCallback(() => {
    return setDenied(true);
  }, []);

  if (denied || isChatAccessError(query.error)) {
    return <ChatUnavailable />;
  }

  if (query.isPending || (query.isFetching && !query.isFetchedAfterMount)) {
    return (
      <View className="gap-3 p-5" accessibilityLabel="채팅 불러오는 중">
        <Skeleton className="h-12 w-2/3 rounded-lg" />
        <Skeleton className="h-12 w-1/2 rounded-lg" />
      </View>
    );
  }

  if (query.isError) {
    return <ChatStatus message="채팅을 불러오지 못했어요." onRetry={() => {
      return void query.refetch();
    }} />;
  }

  return <ChatTimeline roomId={roomId} history={query.data} onAccessDenied={denyAccess} />;
}

function ChatTimeline({ roomId, history, onAccessDenied }: {
  roomId: number;
  history: ChatHistoryDto;
  onAccessDenied: () => void;
}) {
  const client = useQueryClient();
  const [messages, setMessages] = useState(history.items);
  const [hasOlder, setHasOlder] = useState(history.hasMore);
  const [lastRead, setLastRead] = useState(history.chatReadState.lastReadMessageId);

  const [seen, setSeen] = useState<ReadonlySet<number>>(() => {
    return new Set();
  });

  const [isViewing, setIsViewing] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [olderError, setOlderError] = useState(false);
  const [readError, setReadError] = useState(false);
  const screen = useRef<AbortController | null>(null);
  const readInFlight = useRef<AbortSignal | null>(null);
  const olderInFlight = useRef(false);

  const addMessages = useCallback((incoming: ChatMessageDto[]) => {
    setMessages((current) => {
      return mergeChatMessages(current, incoming);
    });
  }, []);

  const { connected, confirmedThrough } = useChatConnection(
    roomId, history.items.at(-1)?.id ?? 0, addMessages, onAccessDenied,
  );

  useFocusEffect(useCallback(() => {
    function setActive(active: boolean) {
      if (active) {
        screen.current ??= new AbortController();
      } else {
        screen.current?.abort();
        screen.current = null;
      }

      setIsViewing(active);
    }

    const subscription = AppState.addEventListener("change", (state) => {
      return setActive(state === "active");
    });

    setActive(AppState.currentState === "active");

    return () => {
      subscription.remove();
      setActive(false);
    };
  }, []));

  const onViewableItemsChanged = useCallback<NonNullable<FlatListProps<ChatMessageDto>["onViewableItemsChanged"]>>(({ viewableItems }) => {
    if (!screen.current || AppState.currentState !== "active") {
      return;
    }

    setSeen((current) => {
      const next = new Set(current);

      for (const { item, isViewable } of viewableItems) {
        if (isViewable) {
          next.add(item.id);
        }
      }

      return next.size === current.size ? current : next;
    });
  }, []);

  const target = getSeenReadPosition(messages, seen, lastRead, hasOlder, confirmedThrough);

  useEffect(() => {
    const signal = screen.current?.signal;

    if (!isViewing || !signal || target === null || readInFlight.current === signal || readError) {
      return;
    }

    readInFlight.current = signal;

    void updateChatReadPosition(roomId, target, signal).then((state) => {
      if (!signal.aborted) {
        setLastRead(state.lastReadMessageId);
        void client.invalidateQueries({ queryKey: roomKeys.list() });
        void client.invalidateQueries({ queryKey: roomKeys.detail(roomId) });
      }
    }).catch((error: unknown) => {
      if (!signal.aborted) {
        if (isChatAccessError(error)) {
          onAccessDenied();
        } else {
          setReadError(true);
        }
      }
    }).finally(() => {
      if (readInFlight.current === signal) {
        readInFlight.current = null;
      }
    });
  }, [client, isViewing, onAccessDenied, readError, roomId, target]);

  async function loadOlder() {
    const signal = screen.current?.signal;
    const beforeMessageId = messages[0]?.id;

    if (!signal || !beforeMessageId || olderInFlight.current) {
      return;
    }

    olderInFlight.current = true;
    setLoadingOlder(true);
    setOlderError(false);

    try {
      const page = await getChatHistory(roomId, { beforeMessageId }, signal);

      if (!signal.aborted) {
        addMessages(page.items);
        setHasOlder(page.hasMore);
      }
    } catch (error) {
      if (!signal.aborted) {
        if (isChatAccessError(error)) {
          onAccessDenied();
        } else {
          setOlderError(true);
        }
      }
    } finally {
      olderInFlight.current = false;
      setLoadingOlder(false);
    }
  }

  const newestFirst = useMemo(() => {
    return [...messages].reverse();
  }, [messages]);

  return (
    <>
      {!__DEV__ && !connected && <Text className="px-5 py-2 text-label text-muted-foreground">실시간 연결 중</Text>}
      {readError && (
        <Button variant="outline" onPress={() => {
          return setReadError(false);
        }}>
          <Text>읽음 저장 다시 시도</Text>
        </Button>
      )}
      {messages.length === 0 ? <ChatStatus message="아직 채팅 메시지가 없어요." /> : (
        <FlatList
          inverted
          data={newestFirst}
          keyExtractor={(item) => {
            return String(item.id);
          }}
          onViewableItemsChanged={onViewableItemsChanged}
          viewabilityConfig={VIEWABILITY_CONFIG}
          maintainVisibleContentPosition={{ minIndexForVisible: 0 }}
          contentContainerClassName="gap-3 p-5"
          ListFooterComponent={hasOlder ? (
            <Button variant="outline" loading={loadingOlder} onPress={() => {
              return void loadOlder();
            }}>
              <Text>{olderError ? "이전 메시지 다시 불러오기" : "이전 메시지 보기"}</Text>
            </Button>
          ) : undefined}
          renderItem={({ item }) => {
            return (
              <View className="gap-1 self-start rounded-lg bg-muted px-4 py-3">
                <Text className="text-label text-foreground-secondary">{item.sender.nickname}</Text>
                <Text className="text-body-sm">{item.body}</Text>
              </View>
            );
          }}
          testID="room-chat.messages"
        />
      )}
    </>
  );
}
