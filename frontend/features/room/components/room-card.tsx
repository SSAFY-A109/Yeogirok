import { Pressable, View } from "react-native";

import { Badge } from "@/components/ui/badge";
import { Text } from "@/components/ui/text";
import { AvatarStack } from "@/features/room/components/member-avatar";
import { hasTripDates, TRANSPORT_MODE_LABEL, type RoomSummary } from "@/features/room/model";
import { formatDateRange, formatDDay } from "@/lib/date";
import { cn } from "@/lib/utils";

/** "기록 보기" 글자(높이 18)의 터치 영역을 44로 넓히는 여백. */
const RECORD_LINK_HIT_SLOP = { top: 13, bottom: 13, left: 8, right: 8 };

/** 카드 전체가 한 요소로 읽혀 안쪽 "기록 보기"에 닿지 않으므로, 스크린 리더 동작으로 같은 기능을 둔다. */
const VIEW_RECORD_ACTION = { name: "viewRecord", label: "기록 보기" } as const;

/** 다가오는 여행의 보조 줄. `11월 8일 (일) – 9일 (월) 자차`, 날짜가 없으면 `날짜 미정 대중교통`. */
function getUpcomingMeta(room: RoomSummary): string {
  const dateText = hasTripDates(room) ? formatDateRange(room.startDate, room.endDate) : "날짜 미정";
  return `${dateText} ${TRANSPORT_MODE_LABEL[room.transportMode]}`;
}

/** 지난 여행의 보조 줄. `10월 3일 – 4일 2명`. */
function getPastMeta(room: RoomSummary): string {
  const dateText = hasTripDates(room) ? formatDateRange(room.startDate, room.endDate, { weekday: false }) : "날짜 미정";
  return `${dateText} ${room.memberCount}명`;
}

type RoomCardProps = {
  room: RoomSummary;
  /** 지난 여행이면 흐린 제목·"완료" 배지·인원 수를 보여 준다. */
  past: boolean;
  onPress: () => void;
  onPressRecord: () => void;
};

/** 여행방 목록 카드(Figma W07 `RoomCard`). */
export function RoomCard({ room, past, onPress, onPressRecord }: RoomCardProps) {
  const meta = past ? getPastMeta(room) : getUpcomingMeta(room);
  const dDay = !past && room.startDate !== null ? formatDDay(room.startDate) : null;
  const statusLabel = past ? "완료" : dDay;
  const hasRecordLink = past && room.recordId !== null;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={[room.name, meta, statusLabel].filter(Boolean).join(", ")}
      accessibilityActions={hasRecordLink ? [VIEW_RECORD_ACTION] : undefined}
      className="gap-3 rounded-md border border-border bg-card p-4 active:bg-muted"

      onAccessibilityAction={(event) => {
        if (event.nativeEvent.actionName === VIEW_RECORD_ACTION.name) {
          onPressRecord();
        }
      }}

      onPress={onPress}
      testID={`room-list.card-${room.id}`}
    >
      <View className="flex-row items-center justify-between gap-3">
        <View className="flex-1 gap-1">
          <Text className={cn("text-h3", past ? "text-foreground-secondary" : "text-foreground")} numberOfLines={1}>
            {room.name}
          </Text>
          <Text className="text-caption text-foreground-secondary" numberOfLines={1}>
            {meta}
          </Text>
        </View>
        {dDay ? (
          <Badge className="self-center">
            <Text className="text-primary">{dDay}</Text>
          </Badge>
        ) : null}
        {past ? (
          <Badge className="self-center">
            <Text className="text-positive">완료</Text>
          </Badge>
        ) : null}
      </View>
      <View className="flex-row items-center justify-between">
        <AvatarStack members={room.members} />
        {hasRecordLink ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`${room.name} 기록 보기`}
            hitSlop={RECORD_LINK_HIT_SLOP}
            onPress={onPressRecord}
            testID={`room-list.record-${room.id}`}
          >
            <Text className="text-caption text-foreground-secondary">기록 보기</Text>
          </Pressable>
        ) : null}
      </View>
    </Pressable>
  );
}
