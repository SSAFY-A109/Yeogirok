import { router } from "expo-router";
import { ChevronLeft } from "lucide-react-native";
import { useMemo, type ReactNode } from "react";
import { KeyboardAvoidingView, Platform, View } from "react-native";

import { isApiError } from "@/api/errors";
import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { HeaderIconButton } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useChatConnection } from "@/features/room/api/chat-connection";
import { useChatMessages, useChatReadReporter } from "@/features/room/api/chat-queries";
import { useRoom } from "@/features/room/api/queries";
import { joinChatPages } from "@/features/room/chat-model";
import { ChatComposer } from "@/features/room/components/chat-composer";
import { ChatMessageList } from "@/features/room/components/chat-message-list";
import { getRoomErrorMessage, ROOM_ACCESS_ERROR_CODES, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import { useMe } from "@/features/user/api/queries";
import { cn } from "@/lib/utils";

const ROOM_NOT_FOUND_MESSAGE = "여행방을 찾을 수 없어요.";

/** 로딩 스켈레톤의 말풍선 자리. `mine`이면 오른쪽에 둔다. */
const SKELETON_BUBBLES = [
  { mine: false, width: "w-40" },
  { mine: false, width: "w-56" },
  { mine: true, width: "w-48" },
  { mine: false, width: "w-32" },
] as const;

function isRoomAccessError(error: unknown): boolean {
  return isApiError(error) && ROOM_ACCESS_ERROR_CODES.includes(error.code);
}

/** 이전 화면으로 돌아간다. 돌아갈 화면이 없으면(딥링크 진입) 여행방 상세로, 여행방을 모르면 목록으로 바꿔 이동한다. */
function leaveChat(roomId: number | null) {
  if (router.canGoBack()) {
    router.back();
    return;
  }

  if (roomId === null) {
    router.replace("/rooms");
    return;
  }

  router.replace({ pathname: "/rooms/[roomId]", params: { roomId: String(roomId) } });
}

/**
 * 여행방 채팅(Figma W09-2, TRP-11). 1단계는 이력 조회와 읽은 위치 갱신까지이고, 보내기는 실시간 연결(`useChatConnection`)이 준비되면 열린다.
 * `roomId`가 `null`이면 여행방을 찾을 수 없다는 안내를 보여 준다.
 */
export function ChatScreen({ roomId }: { roomId: number | null }) {
  if (roomId === null) {
    return (
      <ChatFrame roomId={null} title="">
        <ChatUnavailable message={ROOM_NOT_FOUND_MESSAGE} />
      </ChatFrame>
    );
  }

  return <ChatRoom key={roomId} roomId={roomId} />;
}

function ChatRoom({ roomId }: { roomId: number }) {
  const room = useRoom(roomId);
  const me = useMe();
  const history = useChatMessages(roomId);
  const connection = useChatConnection(roomId);
  const reportSeenMessage = useChatReadReporter(roomId);

  const messages = useMemo(() => {
    return history.data ? joinChatPages(history.data.pages) : [];
  }, [history.data]);

  const accessError = [history.error, room.error].find(isRoomAccessError);

  if (accessError !== undefined) {
    return (
      <ChatFrame roomId={roomId} title="">
        <ChatUnavailable message={getRoomErrorMessage(accessError, ROOM_ERROR_FALLBACK.chat)} />
      </ChatFrame>
    );
  }

  const title = room.data?.name ?? "";
  const subtitle = room.data ? `${room.data.memberCount}명 참여 중` : undefined;

  if (room.data && me.data && history.data) {
    return (
      <ChatFrame roomId={roomId} title={title} subtitle={subtitle}>
        <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"} className="flex-1">
          {messages.length === 0 ? (
            <ChatEmpty />
          ) : (
            <ChatMessageList
              messages={messages}
              members={room.data.members}
              myUserId={me.data.id}
              hasOlder={history.hasNextPage}
              loadingOlder={history.isFetchingNextPage}
              olderError={history.isFetchNextPageError ? getRoomErrorMessage(history.error, ROOM_ERROR_FALLBACK.chatHistory) : null}

              onLoadOlder={() => {
                void history.fetchNextPage();
              }}

              onMessageSeen={reportSeenMessage}
            />
          )}
          <ChatComposer status={connection.status} onSend={connection.send} />
        </KeyboardAvoidingView>
      </ChatFrame>
    );
  }

  const failedQueries = [history, room, me].filter((query) => {
    return query.isError;
  });

  if (failedQueries.length > 0) {
    return (
      <ChatFrame roomId={roomId} title={title} subtitle={subtitle}>
        <ChatError
          message={getRoomErrorMessage(failedQueries[0].error, ROOM_ERROR_FALLBACK.chat)}

          retrying={failedQueries.some((query) => {
            return query.isFetching;
          })}

          onRetry={() => {
            for (const query of failedQueries) {
              void query.refetch();
            }
          }}
        />
      </ChatFrame>
    );
  }

  return (
    <ChatFrame roomId={roomId} title={title} subtitle={subtitle}>
      <ChatSkeleton />
    </ChatFrame>
  );
}

// #region 화면 틀

type ChatFrameProps = {
  roomId: number | null;
  title: string;
  /** 제목 아래 작은 줄(`3명 참여 중`). */
  subtitle?: string;
  children: ReactNode;
};

/**
 * 화면 틀과 상단 바. 공용 `ScreenHeader`는 제목 아래 줄이 없어, 같은 높이(56)·제목 글자(`text-h3`)로 이 화면에서 구성한다.
 * Figma의 "N명 실시간 연결됨"과 초록 점은 실시간 연결을 붙이는 다음 단계에서 넣는다.
 */
function ChatFrame({ roomId, title, subtitle, children }: ChatFrameProps) {
  return (
    <Screen>
      <View className="h-14 flex-row items-center justify-between border-b border-border bg-card px-2" testID="chat.header">
        <HeaderIconButton
          icon={ChevronLeft}
          accessibilityLabel="뒤로 가기"

          onPress={() => {
            leaveChat(roomId);
          }}

          testID="chat.header.back"
        />
        <View className="flex-1 items-center px-2">
          <Text accessibilityRole="header" className="text-h3 text-foreground" numberOfLines={1} testID="chat.title">
            {title}
          </Text>
          {subtitle === undefined ? null : (
            <Text className="text-caption text-foreground-secondary" numberOfLines={1} testID="chat.member-count">
              {subtitle}
            </Text>
          )}
        </View>
        <View className="w-touch" />
      </View>
      {children}
    </Screen>
  );
}

// #endregion

// #region 상태

function ChatEmpty() {
  return (
    <View className="flex-1 items-center justify-center gap-2 px-5" testID="chat.empty">
      <Text className="text-center text-h3 text-foreground">아직 대화가 없어요</Text>
      <Text className="text-center text-body-sm text-foreground-secondary">일행과 나눈 이야기가 여기에 보여요.</Text>
    </View>
  );
}

function ChatSkeleton() {
  return (
    <View
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel="대화를 불러오는 중"
      accessibilityState={{ busy: true }}
      className="flex-1 gap-4 px-5 py-4"
      testID="chat.loading"
    >
      <Skeleton className="h-6 w-24 self-center rounded-full" />
      {SKELETON_BUBBLES.map(({ mine, width }, index) => {
        return (
          <View key={index} className={cn("flex-row items-start gap-2", mine && "justify-end")}>
            {mine ? null : <Skeleton className="h-8 w-8 rounded-full" />}
            <Skeleton className={cn("h-10 rounded-lg", width)} />
          </View>
        );
      })}
    </View>
  );
}

function ChatError({ message, retrying, onRetry }: { message: string; retrying: boolean; onRetry: () => void }) {
  return (
    <View className="flex-1 items-center justify-center gap-4 px-5" testID="chat.error">
      <Text accessibilityRole="alert" className="text-center text-body-sm text-foreground-secondary">
        {message}
      </Text>
      <Button variant="outline" loading={retrying} loadingLabel="불러오는 중" onPress={onRetry} testID="chat.retry">
        <Text>다시 시도</Text>
      </Button>
    </View>
  );
}

/** 여행방이 없거나 내가 참여자가 아닐 때. 다시 시도해도 풀리지 않아 돌아가기만 둔다. */
function ChatUnavailable({ message }: { message: string }) {
  return (
    <View className="flex-1 items-center justify-center gap-4 px-5" testID="chat.unavailable">
      <Text accessibilityRole="alert" className="text-center text-h3 text-foreground">
        {message}
      </Text>
      <Button
        variant="outline"

        onPress={() => {
          if (router.canGoBack()) {
            router.back();
            return;
          }

          router.dismissTo("/rooms");
        }}

        testID="chat.unavailable-back"
      >
        <Text>돌아가기</Text>
      </Button>
    </View>
  );
}

// #endregion
