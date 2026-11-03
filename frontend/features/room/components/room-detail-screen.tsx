import { router } from "expo-router";
import { Pencil } from "lucide-react-native";
import { useState } from "react";
import { View } from "react-native";

import { isApiError } from "@/api/errors";
import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { HeaderIconButton, ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useRoom, useRoomPlaces } from "@/features/room/api/queries";
import type { AddPlaceSource } from "@/features/room/components/add-place-options";
import { AddPlaceSheet } from "@/features/room/components/add-place-sheet";
import { PlaceTabs, type PlaceTab } from "@/features/room/components/place-tabs";
import { RoomDetailSchedule } from "@/features/room/components/room-detail-schedule";
import { RoomInfo } from "@/features/room/components/room-info";
import { getVisibleRoomPlaces, useRoomPlaceRemoval } from "@/features/room/components/room-place-delete";
import { RoomPlaceList } from "@/features/room/components/room-place-list";
import { ScheduleReadinessBar } from "@/features/room/components/schedule-readiness-bar";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import { getScheduleReadiness, type RoomDetail } from "@/features/room/model";

const NOT_FOUND_STATUS = 404;

function isRoomNotFound(error: unknown): boolean {
  return isApiError(error) && error.status === NOT_FOUND_STATUS;
}

/** 담는 경로별 화면으로 간다. 코스 화면은 아직 없어 메인 탭으로 보낸다. */
function goToPlaceSource(roomId: number, source: AddPlaceSource) {
  const params = { roomId: String(roomId) };

  switch (source) {
    case "course":
      router.navigate("/");
      return;
    case "ai":
      router.push({ pathname: "/rooms/[roomId]/recommend", params });
      return;
    case "search":
      router.push({ pathname: "/rooms/[roomId]/search", params });
      return;
  }
}

type RoomDetailScreenProps = {
  /** 검증한 라우트 파라미터. 잘못된 id면 `null`. */
  roomId: number | null;
};

/** 여행방 상세(Figma W09 + W09-A 시트 + W09-S 하단 상태, TRP-04). */
export function RoomDetailScreen({ roomId }: RoomDetailScreenProps) {
  if (roomId === null) {
    return <RoomNotFound />;
  }

  return <RoomDetailQuery roomId={roomId} />;
}

function RoomDetailQuery({ roomId }: { roomId: number }) {
  const roomQuery = useRoom(roomId);

  if (roomQuery.data) {
    return <RoomDetailContent room={roomQuery.data} />;
  }

  if (roomQuery.isError) {
    if (isRoomNotFound(roomQuery.error)) {
      return <RoomNotFound />;
    }

    return (
      <Screen>
        <ScreenHeader title="" testID="room-detail.header" />
        <View className="flex-1 items-center justify-center gap-4 px-5 pb-12">
          <Text className="text-center text-body-sm text-foreground-secondary">
            {getRoomErrorMessage(roomQuery.error, ROOM_ERROR_FALLBACK.load)}
          </Text>
          <Button
            variant="outline"
            loading={roomQuery.isFetching}
            loadingLabel="불러오는 중"

            onPress={() => {
              void roomQuery.refetch();
            }}

            testID="room-detail.retry"
          >
            <Text>다시 시도</Text>
          </Button>
        </View>
      </Screen>
    );
  }

  return <RoomDetailLoading />;
}

// #region 여행방 상세 본문

function RoomDetailContent({ room }: { room: RoomDetail }) {
  const [tab, setTab] = useState<PlaceTab>("places");
  const [sheetOpen, setSheetOpen] = useState(false);
  const placesQuery = useRoomPlaces(room.id);
  const { hiddenIds, remove } = useRoomPlaceRemoval(room.id);
  const toast = useToast();

  const visiblePlaceCount = placesQuery.data ? getVisibleRoomPlaces(placesQuery.data, hiddenIds).length : room.placeCount;
  const readiness = getScheduleReadiness(room, visiblePlaceCount);
  const params = { roomId: String(room.id) };

  const goToEdit = () => {
    router.push({ pathname: "/rooms/[roomId]/edit", params });
  };

  const goToInvite = () => {
    router.push({ pathname: "/rooms/[roomId]/invite", params });
  };

  const openSheet = () => {
    setSheetOpen(true);
  };

  const selectSource = (source: AddPlaceSource) => {
    setSheetOpen(false);
    goToPlaceSource(room.id, source);
  };

  const showSchedulePending = () => {
    toast.show({ message: "AI 일정 만들기는 아직 준비 중이에요" });
  };

  const header = (
    <>
      <RoomInfo room={room} onInvite={goToInvite} />
      <PlaceTabs value={tab} onValueChange={setTab} placeCount={visiblePlaceCount} />
    </>
  );

  return (
    <Screen>
      <ScreenHeader
        title={room.name}
        titleAlign="start"
        trailing={<HeaderIconButton icon={Pencil} accessibilityLabel="여행방 정보 수정" onPress={goToEdit} testID="room-detail.edit" />}
        testID="room-detail.header"
      />
      <View className="flex-1">
        {tab === "places" ? (
          <RoomPlaceList
            roomId={room.id}
            members={room.members}
            hiddenIds={hiddenIds}
            header={header}
            onDeletePlace={remove}
            onAddPlace={openSheet}
            onSelectSource={selectSource}
          />
        ) : (
          <RoomDetailSchedule room={room} header={header} />
        )}
      </View>
      <ScheduleReadinessBar
        readiness={readiness}
        room={room}
        placeCount={visiblePlaceCount}
        onSetDates={goToEdit}
        onAddPlace={openSheet}
        onCreateSchedule={showSchedulePending}
      />
      <AddPlaceSheet open={sheetOpen} onOpenChange={setSheetOpen} onSelect={selectSource} />
    </Screen>
  );
}

// #endregion

// #region 찾을 수 없음·로딩

function RoomNotFound() {
  return (
    <Screen>
      <ScreenHeader title="" testID="room-detail.header" />
      <View className="flex-1 items-center justify-center gap-4 px-5 pb-12">
        <Text className="text-center text-h3 text-foreground">여행방을 찾을 수 없어요</Text>
        <Button
          variant="outline"

          onPress={() => {
            router.navigate("/rooms");
          }}

          testID="room-detail.go-to-list"
        >
          <Text>여행방 목록으로</Text>
        </Button>
      </View>
    </Screen>
  );
}

function RoomDetailLoading() {
  return (
    <Screen>
      <ScreenHeader title="" testID="room-detail.header" />
      <View
        accessibilityRole="progressbar"
        accessibilityLabel="여행방 정보를 불러오는 중"
        accessibilityState={{ busy: true }}
        className="gap-4 px-5 pt-1"
      >
        <View className="flex-row items-center gap-2">
          <Skeleton className="h-6 w-12 rounded-xs" />
          <Skeleton className="h-4 w-40 rounded-xs" />
        </View>
        <View className="flex-row items-center gap-2">
          <Skeleton className="h-7 w-16 rounded-full" />
          <Skeleton className="h-4 w-28 rounded-xs" />
        </View>
        <Skeleton className="h-11 w-full rounded-sm" />
        {[0, 1, 2].map((index) => {
          return (
            <View key={index} className="flex-row items-center gap-2 py-1">
              <Skeleton className="h-input w-input" />
              <View className="flex-1 gap-2">
                <Skeleton className="h-4 w-32 rounded-xs" />
                <Skeleton className="h-3 w-44 rounded-xs" />
              </View>
            </View>
          );
        })}
      </View>
    </Screen>
  );
}

// #endregion
