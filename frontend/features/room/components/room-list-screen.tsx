import { router } from "expo-router";
import { Plus, Users } from "lucide-react-native";
import { SectionList, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useRooms } from "@/features/room/api/queries";
import { RoomCard } from "@/features/room/components/room-card";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import { isPastTrip, type RoomSummary } from "@/features/room/model";
import { todayLocalDate } from "@/lib/date";

/** 36 높이 "새 여행방" 버튼의 터치 영역을 44로 넓히는 여백. */
const CREATE_BUTTON_HIT_SLOP = 4;
const LOADING_CARD_COUNT = 3;

type RoomSection = { key: "upcoming" | "past"; title: string; data: RoomSummary[] };

/** 다가오는 여행(날짜 미정 포함)과 지난 여행으로 나눈다. 빈 묶음은 뺀다. */
function groupRooms(rooms: RoomSummary[]): RoomSection[] {
  const today = todayLocalDate();

  const sections: RoomSection[] = [
    {
      key: "upcoming",
      title: "다가오는 여행",

      data: rooms.filter((room) => {
        return !isPastTrip(room, today);
      }),
    },
    {
      key: "past",
      title: "지난 여행",

      data: rooms.filter((room) => {
        return isPastTrip(room, today);
      }),
    },
  ];

  return sections.filter((section) => {
    return section.data.length > 0;
  });
}

const goToCreate = () => {
  router.push("/rooms/new");
};

/** 여행방 목록(Figma W07, TRP-01). 참여한 여행방만 보인다. */
export function RoomListScreen() {
  return (
    <Screen>
      <View className="flex-row items-center justify-between px-5 pb-4 pt-2">
        <Text accessibilityRole="header" className="text-h1 text-foreground">
          여행방
        </Text>
        <Button
          variant="outline"
          accessibilityLabel="새 여행방 만들기"
          className="h-button-sm min-h-button-sm gap-1 px-3 py-0"
          hitSlop={CREATE_BUTTON_HIT_SLOP}
          onPress={goToCreate}
          testID="room-list.create-button"
        >
          <Icon as={Plus} size={16} className="text-primary" />
          <Text>새 여행방</Text>
        </Button>
      </View>
      <RoomListBody />
    </Screen>
  );
}

// #region 목록 본문

function RoomListBody() {
  const roomsQuery = useRooms();
  const toast = useToast();

  if (roomsQuery.data) {
    if (roomsQuery.data.length === 0) {
      return <RoomListEmpty />;
    }

    return (
      <SectionList
        sections={groupRooms(roomsQuery.data)}
        keyExtractor={(room) => {
          return String(room.id);
        }}
        stickySectionHeadersEnabled={false}

        renderSectionHeader={({ section }) => {
          return (
            <Text accessibilityRole="header" className="px-5 pb-3 pt-2 text-label-sm text-muted-foreground">
              {section.title}
            </Text>
          );
        }}

        renderItem={({ item, section }) => {
          return (
            <View className="px-5 pb-3">
              <RoomCard
                room={item}
                past={section.key === "past"}

                onPress={() => {
                  router.push({ pathname: "/rooms/[roomId]", params: { roomId: String(item.id) } });
                }}

                onPressRecord={() => {
                  toast.show({ message: "여행 기록은 아직 준비 중이에요" });
                }}
              />
            </View>
          );
        }}
      />
    );
  }

  if (roomsQuery.isError) {
    return (
      <View className="flex-1 items-center justify-center gap-4 px-5 pb-12">
        <Text className="text-center text-body-sm text-foreground-secondary">
          {getRoomErrorMessage(roomsQuery.error, ROOM_ERROR_FALLBACK.load)}
        </Text>
        <Button
          variant="outline"
          loading={roomsQuery.isFetching}
          loadingLabel="불러오는 중"

          onPress={() => {
            void roomsQuery.refetch();
          }}

          testID="room-list.retry"
        >
          <Text>다시 시도</Text>
        </Button>
      </View>
    );
  }

  return <RoomListLoading />;
}

function RoomListEmpty() {
  return (
    <View className="px-5 pt-2">
      <View className="items-center gap-3 rounded-md bg-muted p-6">
        <Icon as={Users} size={28} className="text-icon" />
        <Text className="text-label text-foreground">아직 여행방이 없어요</Text>
        <Button onPress={goToCreate} testID="room-list.empty-create-button">
          <Text>새 여행방 만들기</Text>
        </Button>
      </View>
    </View>
  );
}

function RoomListLoading() {
  return (
    <View
      accessibilityRole="progressbar"
      accessibilityLabel="여행방 목록을 불러오는 중"
      accessibilityState={{ busy: true }}
      className="gap-3 px-5 pt-2"
    >
      <Skeleton className="h-4 w-20 rounded-xs" />
      {Array.from({ length: LOADING_CARD_COUNT }, (_, index) => {
        return (
          <View key={index} className="gap-3 rounded-md border border-border p-4">
            <Skeleton className="h-6 w-32 rounded-xs" />
            <Skeleton className="h-3 w-44 rounded-xs" />
            <Skeleton className="h-7 w-16 rounded-full" />
          </View>
        );
      })}
    </View>
  );
}

// #endregion
