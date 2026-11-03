import { Plus } from "lucide-react-native";
import { Pressable, View } from "react-native";

import { Badge } from "@/components/ui/badge";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { AvatarStack } from "@/features/room/components/member-avatar";
import { hasTripDates, isPastTrip, TRANSPORT_MODE_LABEL, type RoomDetail } from "@/features/room/model";
import { formatDateRange, formatDDay } from "@/lib/date";

/** 28 높이 초대 칩의 터치 영역을 44로 넓히는 여백. */
const INVITE_CHIP_HIT_SLOP = 8;

/** 출발일까지 남은 날 배지. 날짜가 없거나 지난 여행이면 `null`. */
function getRoomDDay(room: RoomDetail): string | null {
  if (room.startDate === null || isPastTrip(room)) {
    return null;
  }

  return formatDDay(room.startDate);
}

type RoomInfoProps = {
  room: RoomDetail;
  onInvite: () => void;
};

/** 여행방 상세 위쪽 정보(Figma W09 `Room info`): D-day·날짜·이동 수단, 일행과 초대 칩. */
export function RoomInfo({ room, onInvite }: RoomInfoProps) {
  const dDay = getRoomDDay(room);
  const dateText = hasTripDates(room) ? formatDateRange(room.startDate, room.endDate) : "날짜 미정";

  return (
    <View className="gap-3 px-5 pb-4 pt-1">
      <View className="flex-row items-center gap-2">
        {dDay ? (
          <Badge className="self-center">
            <Text className="text-primary">{dDay}</Text>
          </Badge>
        ) : null}
        <Text className="shrink text-caption text-foreground-secondary">
          {`${dateText} / ${TRANSPORT_MODE_LABEL[room.transportMode]}`}
        </Text>
      </View>
      <View className="flex-row items-center gap-2">
        <AvatarStack members={room.members} />
        <Text className="shrink text-caption text-foreground-secondary">{`${room.memberCount}명이 함께 계획 중`}</Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="일행 초대"
          className="h-7 flex-row items-center gap-1 rounded-full border border-ring px-3 active:bg-accent"
          hitSlop={INVITE_CHIP_HIT_SLOP}
          onPress={onInvite}
          testID="room-detail.invite"
        >
          <Icon as={Plus} size={14} className="text-primary" />
          <Text className="text-label-sm text-primary">초대</Text>
        </Pressable>
      </View>
    </View>
  );
}
