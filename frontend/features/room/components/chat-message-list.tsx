import { useMemo } from "react";
import { FlatList, Pressable, View } from "react-native";

import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import type { ChatMessage } from "@/features/room/chat-model";
import { MemberAvatar } from "@/features/room/components/member-avatar";
import type { RoomMember } from "@/features/room/model";
import { formatMonthDay, type LocalDate } from "@/lib/date";

/** 행의 절반 이상이 화면에 보이면 그 메시지를 본 것으로 친다. 목록이 그려진 뒤에는 바꿀 수 없어 상수로 둔다. */
const VIEWABILITY_CONFIG = { itemVisiblePercentThreshold: 50 };

type ChatRow =
  | { kind: "message"; key: string; message: ChatMessage }
  | { kind: "date"; key: string; date: LocalDate };

/**
 * 목록 행. 뒤집힌 목록에 넣으므로 최신 메시지가 맨 앞이고, 날짜 칩은 그날 첫 메시지 뒤(화면에서는 위)에 둔다.
 * @param messages 오래된 → 최신 순
 */
function toChatRows(messages: readonly ChatMessage[]): ChatRow[] {
  const rows: ChatRow[] = [];

  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    rows.push({ kind: "message", key: `message-${message.id}`, message });

    if (index === 0 || messages[index - 1].sentDate !== message.sentDate) {
      rows.push({ kind: "date", key: `date-before-${message.id}`, date: message.sentDate });
    }
  }

  return rows;
}

/** 보낸 사람 아바타 색. 일행 목록 순서를 따라 화면마다 같은 색이 되게 한다. 탈퇴했거나 나간 사람은 목록에 없다. */
function getSenderToneIndex(members: RoomMember[], senderId: number | null): number {
  if (senderId === null) {
    return members.length;
  }

  const index = members.findIndex((member) => {
    return member.id === senderId;
  });

  return index === -1 ? senderId : index;
}

type ChatMessageListProps = {
  /** 오래된 → 최신 순. 한 개 이상이다. */
  messages: ChatMessage[];
  members: RoomMember[];
  myUserId: number;
  hasOlder: boolean;
  loadingOlder: boolean;
  /** 이전 대화를 받지 못했을 때의 안내 문구. */
  olderError: string | null;
  onLoadOlder: () => void;
  /** 화면에 보인 메시지 가운데 가장 최신 것의 id를 알린다. */
  onMessageSeen: (messageId: number) => void;
};

/** 채팅 메시지 목록(Figma W09-2). 최신 메시지가 아래에 있고, 위로 올리면 이전 대화를 더 받는다. */
export function ChatMessageList({ messages, members, myUserId, hasOlder, loadingOlder, olderError, onLoadOlder, onMessageSeen }: ChatMessageListProps) {
  const rows = useMemo(() => {
    return toChatRows(messages);
  }, [messages]);

  const loadOlderOnReach = () => {
    if (hasOlder && !loadingOlder && olderError === null) {
      onLoadOlder();
    }
  };

  return (
    <FlatList
      inverted
      className="flex-1"
      contentContainerClassName="grow justify-end gap-4 px-5 py-4"
      data={rows}

      keyExtractor={(row) => {
        return row.key;
      }}

      renderItem={({ item }) => {
        if (item.kind === "date") {
          return <ChatDateChip date={item.date} />;
        }

        if (item.message.senderId === myUserId) {
          return <MyMessage message={item.message} />;
        }

        return <MemberMessage message={item.message} toneIndex={getSenderToneIndex(members, item.message.senderId)} />;
      }}

      ListFooterComponent={
        loadingOlder || olderError !== null ? <OlderMessagesStatus loading={loadingOlder} error={olderError} onRetry={onLoadOlder} /> : undefined
      }
      onEndReached={loadOlderOnReach}
      onEndReachedThreshold={0.5}

      onViewableItemsChanged={({ viewableItems }) => {
        const seenIds = viewableItems.flatMap(({ item }) => {
          return item.kind === "message" ? [item.message.id] : [];
        });

        if (seenIds.length > 0) {
          onMessageSeen(Math.max(...seenIds));
        }
      }}

      viewabilityConfig={VIEWABILITY_CONFIG}
      keyboardDismissMode="on-drag"
      keyboardShouldPersistTaps="handled"
      testID="chat.message-list"
    />
  );
}

// #region 날짜 칩·말풍선·이전 대화 상태

function ChatDateChip({ date }: { date: LocalDate }) {
  return (
    <View className="self-center rounded-full bg-muted px-3 py-1">
      <Text accessibilityRole="header" className="text-caption text-foreground-secondary">
        {formatMonthDay(date)}
      </Text>
    </View>
  );
}

function MemberMessage({ message, toneIndex }: { message: ChatMessage; toneIndex: number }) {
  return (
    <View
      accessible
      accessibilityLabel={`${message.senderName}, ${message.body}, ${message.sentTime}`}
      className="flex-row gap-2 pr-4"
      testID={`chat.message-${message.id}`}
    >
      <MemberAvatar initial={message.senderInitial} toneIndex={toneIndex} className="h-8 w-8" />
      <View className="flex-1 gap-1">
        <Text className="text-caption text-foreground-secondary" numberOfLines={1}>
          {message.senderName}
        </Text>
        <View className="flex-row items-end gap-1">
          <View className="shrink rounded-lg bg-muted px-3 py-2">
            <Text className="text-body-sm text-foreground">{message.body}</Text>
          </View>
          <Text className="text-micro tabular-nums text-muted-foreground">{message.sentTime}</Text>
        </View>
      </View>
    </View>
  );
}

function MyMessage({ message }: { message: ChatMessage }) {
  return (
    <View
      accessible
      accessibilityLabel={`나, ${message.body}, ${message.sentTime}`}
      className="flex-row items-end justify-end gap-1 pl-12"
      testID={`chat.message-${message.id}`}
    >
      <Text className="text-micro tabular-nums text-muted-foreground">{message.sentTime}</Text>
      <View className="shrink rounded-lg bg-primary px-3 py-2">
        <Text className="text-body-sm text-primary-foreground">{message.body}</Text>
      </View>
    </View>
  );
}

function OlderMessagesStatus({ loading, error, onRetry }: { loading: boolean; error: string | null; onRetry: () => void }) {
  if (loading) {
    return (
      <View className="items-center py-2" testID="chat.older-loading">
        <Spinner accessibilityLabel="이전 대화를 불러오는 중" />
      </View>
    );
  }

  if (error === null) {
    return null;
  }

  return (
    <View className="flex-row items-center justify-center gap-2" testID="chat.older-error">
      <Text accessibilityRole="alert" className="shrink text-caption text-destructive">
        {error}
      </Text>
      <Pressable
        accessibilityRole="button"
        className="min-h-touch justify-center rounded-sm px-2 active:bg-accent"
        onPress={onRetry}
        testID="chat.older-retry"
      >
        <Text className="text-label text-primary">다시 시도</Text>
      </Pressable>
    </View>
  );
}

// #endregion
