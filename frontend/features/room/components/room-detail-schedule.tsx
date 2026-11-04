import { Calendar } from "lucide-react-native";
import type { ReactElement } from "react";
import { FlatList, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import type { RoomDetail } from "@/features/room/model";
import { useRoomSchedule } from "@/features/schedule/api/queries";
import { getScheduleErrorMessage } from "@/features/schedule/errors";
import { formatMonthDay } from "@/lib/date";

type RoomDetailScheduleProps = {
  room: RoomDetail;
  /** 목록 위에 함께 스크롤되는 여행방 정보·탭. */
  header: ReactElement;
};

/**
 * 여행방 상세의 "일정" 탭. 일정이 없으면 W09-S "일정 탭 빈 상태"를 보여 준다.
 * 일정이 있을 때의 화면은 여행방 일정 만들기 섹션 범위라, 날짜별 장소 이름만 읽기 전용으로 보여 준다.
 */
export function RoomDetailSchedule({ room, header }: RoomDetailScheduleProps) {
  const hasSchedule = room.scheduleCreatedAt !== null;
  const scheduleQuery = useRoomSchedule(room.id, { enabled: hasSchedule });
  const days = scheduleQuery.data?.days ?? [];

  const renderEmpty = () => {
    if (!hasSchedule || scheduleQuery.data !== undefined) {
      return <ScheduleEmpty />;
    }

    if (scheduleQuery.isError) {
      return (
        <View className="items-center gap-4 px-5 py-8">
          <Text className="text-center text-body-sm text-foreground-secondary">{getScheduleErrorMessage(scheduleQuery.error)}</Text>
          <Button
            variant="outline"
            loading={scheduleQuery.isFetching}
            loadingLabel="불러오는 중"

            onPress={() => {
              void scheduleQuery.refetch();
            }}

            testID="room-detail.schedule-retry"
          >
            <Text>다시 시도</Text>
          </Button>
        </View>
      );
    }

    return (
      <View accessibilityRole="progressbar" accessibilityLabel="일정을 불러오는 중" accessibilityState={{ busy: true }} className="gap-3 px-5">
        <Skeleton className="h-5 w-24 rounded-xs" />
        <Skeleton className="h-4 w-40 rounded-xs" />
        <Skeleton className="h-4 w-32 rounded-xs" />
      </View>
    );
  };

  return (
    <FlatList
      data={days}
      keyExtractor={(day) => {
        return day.date;
      }}
      contentContainerClassName="pb-4"
      ListHeaderComponent={header}
      ListEmptyComponent={renderEmpty()}

      renderItem={({ item, index }) => {
        return (
          <View className="px-5">
            <View className={index < days.length - 1 ? "gap-2 border-b border-border py-3" : "gap-2 py-3"}>
              <Text accessibilityRole="header" className="text-label text-foreground">
                {formatMonthDay(item.date)}
              </Text>
              {item.places.map((place, order) => {
                return (
                  <Text key={place.id} className="text-body-sm text-foreground-secondary">
                    {`${order + 1}. ${place.name}`}
                  </Text>
                );
              })}
            </View>
          </View>
        );
      }}
    />
  );
}

function ScheduleEmpty() {
  return (
    <View className="px-5">
      <View className="items-center gap-2 rounded-md bg-muted p-6" testID="room-detail.schedule-empty">
        <Icon as={Calendar} size={28} className="text-icon" />
        <Text className="text-label text-foreground">아직 일정이 없어요</Text>
        <Text className="text-center text-caption text-foreground-secondary">후보 장소로 AI 일정 초안을 만들어 보세요</Text>
      </View>
    </View>
  );
}
