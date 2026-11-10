import { router } from "expo-router";
import { ChevronRight, Plus } from "lucide-react-native";
import { Pressable, ScrollView, View } from "react-native";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useRooms } from "@/features/room/api/queries";
import { AvatarStack } from "@/features/room/components/member-avatar";
import type { RoomSummary } from "@/features/room/model";
import { useCreateRecord, useRecords } from "../api/queries";
import { recordError } from "../errors";
import { canCreateRecord, recordDateLabel, type RecordListItem } from "../model";

export function openRecord(id: number) {
  router.push({ pathname: "/memo/[recordId]", params: { recordId: String(id) } });
}

export function RecordListScreen() {
  const records = useRecords();
  const rooms = useRooms();

  const items = records.data?.pages.flatMap((page) => {
    return page.items;
  }) ?? [];

  const occupied = new Set(items.map((item) => {
    return item.roomId;
  }));

  const available = (rooms.data ?? []).filter((room) => {
    return !room.recordId && !occupied.has(room.id) && canCreateRecord(room.endDate);
  });

  const drafts = items.filter((item) => {
    return item.status !== "COMPLETED";
  });

  const complete = items.filter((item) => {
    return item.status === "COMPLETED";
  });

  return (
    <Screen testID="memo.screen">
      <ScreenHeader title="내 기록" titleAlign="start" onBack={() => {
        return router.canGoBack() ? router.back() : router.replace("/my");
      }} />
      <ScrollView contentContainerClassName="gap-5 px-5 pb-6 pt-2">
        {records.isPending ? <RecordLoading /> : records.isError && !records.data ? (
          <RecordIssue message={recordError(records.error, "기록을 불러오지 못했어요.")} onRetry={() => {
            return void records.refetch();
          }} />
        ) : (
          <>
            <RecordGroup label="작성 중" items={drafts} />
            <RecordGroup label="완성" items={complete} />
            {records.hasNextPage && (
              <Button variant="outline" loading={records.isFetchingNextPage} onPress={() => {
                return void records.fetchNextPage();
              }}>
                <Text>{records.isFetchNextPageError ? "다시 시도" : "기록 더 보기"}</Text>
              </Button>
            )}
          </>
        )}
        {rooms.isPending && !records.isPending && <RecordLoading />}
        {rooms.isError && <RecordIssue message="여행방을 불러오지 못했어요." onRetry={() => {
          return void rooms.refetch();
        }} />}
        {available.length > 0 && (
          <View className="gap-3">
            <Text accessibilityRole="header" className="text-label-sm text-muted-foreground">기록을 기다리는 여행</Text>
            <RoomRecordList rooms={available} />
          </View>
        )}
        {records.isSuccess && rooms.isSuccess && items.length === 0 && available.length === 0 && (
          <Text className="py-10 text-center text-body-sm text-muted-foreground">아직 여행 기록이 없어요. 여행을 마치면 기록을 만들 수 있어요.</Text>
        )}
      </ScrollView>
    </Screen>
  );
}

function RecordGroup({ label, items }: { label: string; items: RecordListItem[] }) {
  if (!items.length) {
    return null;
  }

  return <View className="gap-3">
    <Text accessibilityRole="header" className="text-label-sm text-muted-foreground">{label}</Text>
    {items.map((item) => {
      return <RecordCard key={item.id} item={item} />;
    })}
  </View>;
}

export function RecordCard({ item }: { item: RecordListItem }) {
  const complete = item.status === "COMPLETED";
  const action = complete ? "보기" : "이어서 하기";
  const badge = item.publication ? "코스 공개 중" : complete ? "완성" : item.progress.stage === "REVIEW" ? "초안 확인 필요" : "작성 중";
  const meta = [recordDateLabel(item.startDate, item.endDate), item.regionName, `${item.memberCount}명`].filter(Boolean).join(" ");

  return (
    <Pressable accessibilityRole="button" accessibilityLabel={`${item.title}, ${badge}, ${action}`} onPress={() => {
      return openRecord(item.id);
    }}
    className="gap-4 rounded-md border border-border bg-card p-4 active:bg-muted" testID={`memo.record-${item.id}`}>
      <View className="flex-row items-start justify-between gap-3">
        <View className="flex-1 gap-1">
          <Text className="text-h3">{item.title}</Text>
          <Text className="text-caption text-foreground-secondary">{meta}</Text>
        </View>
        <Badge><Text className={item.publication ? "text-positive" : undefined}>{badge}</Text></Badge>
      </View>
      <View className="flex-row items-center justify-between">
        <AvatarStack members={item.members} />
        <View className="flex-row items-center gap-1">
          <Text className={complete ? "text-label text-foreground-secondary" : "text-label text-primary"}>{action}</Text>
          <Icon as={ChevronRight} size={16} className={complete ? "text-icon" : "text-primary"} />
        </View>
      </View>
    </Pressable>
  );
}

function RoomRecordList({ rooms }: { rooms: RoomSummary[] }) {
  const create = useCreateRecord(openRecord);

  return <View className="gap-3">
    {rooms.map((room) => {
      return <View key={room.id} className="flex-row items-center gap-3 rounded-md bg-muted p-4">
        <View className="flex-1 gap-1">
          <Text className="text-label">{room.name}</Text>
          <Text className="text-caption text-foreground-secondary">{recordDateLabel(room.startDate, room.endDate)} 여행방만 있어요</Text>
        </View>
        <Button variant="outline" loading={create.isPending && create.variables === room.id} disabled={create.isPending}
          accessibilityLabel={`${room.name} 기록 만들기`} onPress={() => {
            return create.mutate(room.id);
          }} testID={`memo.create-${room.id}`}>
          <Icon as={Plus} size={16} className="text-primary" /><Text>기록 만들기</Text>
        </Button>
      </View>;
    })}
    {create.isError && <RecordIssue message={recordError(create.error)} />}
  </View>;
}

export function RecordLoading() {
  return <View className="gap-3 py-2" accessibilityRole="progressbar" accessibilityLabel="기록 불러오는 중">
    <Skeleton className="h-4 w-20" /><Skeleton className="h-28 w-full rounded-md" /><Skeleton className="h-28 w-full rounded-md" />
  </View>;
}

export function RecordIssue({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return <View className="items-start gap-2 rounded-md bg-muted p-4">
    <Text accessibilityRole="alert" className="text-body-sm text-foreground-secondary">{message}</Text>
    {onRetry && <Button variant="outline" onPress={onRetry}><Text>다시 시도</Text></Button>}
  </View>;
}
