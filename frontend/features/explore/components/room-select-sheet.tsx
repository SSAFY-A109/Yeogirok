import { useState, type ReactNode } from "react";
import { Pressable, View } from "react-native";
import { ChevronRight, Plus } from "lucide-react-native";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useAddRoomPlaces, useImportCoursePlaces, useRooms } from "@/features/room/api/queries";
import { getCourseImportErrorMessage, getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import { hasTripDates, type AddRoomPlaceItemDto, type PlaceRefDto, type RoomSummary } from "@/features/room/model";
import { useIdempotencyKeyFor } from "@/hooks/use-idempotency-key";
import { formatDateRange } from "@/lib/date";
import { cn } from "@/lib/utils";
import type { AddPlacesResult } from "../model";
import { InlineMessage } from "./query-states";

/** 담을 장소. 인기 장소는 API-029(검색 담기)로, 공개 코스에서 고른 방문은 API-030으로 담는다. */
export type RoomSelectPlaces =
  | { kind: "search"; placeRefs: PlaceRefDto[] }
  | { kind: "course"; recordId: number; visitIds: number[] };

function countPlaces(places: RoomSelectPlaces): number {
  return places.kind === "search" ? places.placeRefs.length : places.visitIds.length;
}

/** `11월 8일 – 9일`. 날짜가 없으면 `날짜 미정`. */
function formatRoomDates(room: RoomSummary): string {
  return hasTripDates(room) ? formatDateRange(room.startDate, room.endDate, { weekday: false }) : "날짜 미정";
}

type RoomSelectSheetProps = {
  visible: boolean;
  placeNames: string[];
  places: RoomSelectPlaces;
  onClose: () => void;
  onAdded: (result: AddPlacesResult) => void;
  /** 새 여행방 만들기(W06)로 고른 장소를 넘긴다. 없으면 버튼을 준비 중으로 둔다. */
  onCreateRoom?: () => void;
  /** 제목과 여행방 목록 사이에 보여 줄 내용(예: 장소 위치 지도). */
  children?: ReactNode;
};

export function RoomSelectSheet({ visible, placeNames, places, onClose, onAdded, onCreateRoom, children }: RoomSelectSheetProps) {
  const rooms = useRooms({ enabled: visible });
  const addRoomPlaces = useAddRoomPlaces();
  const importPlaces = useImportCoursePlaces();
  const getIdempotencyKey = useIdempotencyKeyFor();
  const [pickedId, setPickedId] = useState<number>();

  const room = rooms.data?.find((item) => {
    return item.id === pickedId;
  }) ?? rooms.data?.[0];

  const placeCount = countPlaces(places);
  const isAdding = addRoomPlaces.isPending || importPlaces.isPending;
  const canCreateRoom = onCreateRoom !== undefined && !isAdding;
  const addErrorMessage = getAddErrorMessage();

  function getAddErrorMessage(): string | null {
    if (places.kind === "course") {
      return importPlaces.error === null ? null : getCourseImportErrorMessage(importPlaces.error);
    }

    return addRoomPlaces.error === null ? null : getRoomErrorMessage(addRoomPlaces.error, ROOM_ERROR_FALLBACK.addPlaces);
  }

  function submit() {
    if (!room || isAdding || placeCount === 0) {
      return;
    }

    const { id: roomId, name: roomName } = room;

    if (places.kind === "course") {
      const request = { recordId: places.recordId, visitIds: places.visitIds };

      importPlaces.mutate(
        { roomId, request, idempotencyKey: getIdempotencyKey(JSON.stringify({ roomId, ...request })) },
        {
          onSuccess: (addedCount) => {
            onAdded({ roomName, addedCount });
          },
        },
      );
      return;
    }

    const items: AddRoomPlaceItemDto[] = places.placeRefs.map((placeRef) => {
      return { placeRef, sourceType: "SEARCH" };
    });

    addRoomPlaces.mutate(
      { roomId, items, idempotencyKey: getIdempotencyKey(JSON.stringify({ roomId, items })) },
      {
        onSuccess: ({ places: addedPlaces }) => {
          onAdded({ roomName, addedCount: addedPlaces.length });
        },
      },
    );
  }

  return (
    <BottomSheet visible={visible} onClose={onClose} closeLabel="여행방 선택 닫기">
      <View className="gap-4 px-5 pt-2" testID="room-sheet">
        <View className="gap-1">
          <Text accessibilityRole="header" className="text-h3 text-foreground">어느 여행방에 담을까요?</Text>
          <Text numberOfLines={1} className="text-caption text-foreground-secondary">{placeNames.join(", ")}</Text>
        </View>
        {children}
        <View className="gap-2">
          {rooms.isPending && (
            <View accessibilityLabel="여행방 불러오는 중" className="gap-2">
              {[0, 1].map((key) => {
                return <Skeleton key={key} className="h-16 w-full" />;
              })}
            </View>
          )}
          {rooms.isError && (
            <InlineMessage title="여행방을 불러오지 못했어요" actionLabel="다시 시도" onAction={() => {
              void rooms.refetch();
            }} />
          )}
          {rooms.data?.length === 0 && (
            <Text className="text-caption text-foreground-secondary" testID="room-sheet.empty">참여 중인 여행방이 없어요. 새 여행방을 만들어 담아 보세요.</Text>
          )}
          {rooms.data && rooms.data.length > 0 && (
            <RadioGroup
              value={room ? String(room.id) : ""}
              onValueChange={(value) => {
                setPickedId(Number(value));
              }}
              className="gap-2"
            >
              {rooms.data.map((item) => {
                const selected = item.id === room?.id;
                const dates = formatRoomDates(item);

                return (
                  <Pressable
                    key={item.id}
                    accessibilityRole="radio"
                    accessibilityLabel={`${item.name}, ${dates}, ${item.memberCount}명, 담은 장소 ${item.placeCount}곳`}
                    accessibilityState={{ checked: selected }}
                    onPress={() => {
                      setPickedId(item.id);
                    }}
                    className={cn("flex-row items-center gap-3 rounded-md p-4", selected ? "border-2 border-ring bg-accent" : "border border-border bg-card")}
                    testID={`room-sheet.room.${item.id}`}
                  >
                    <RadioGroupItem value={String(item.id)} accessible={false} />
                    <View className="flex-1 gap-1">
                      <Text className={cn("text-label", selected ? "text-primary" : "text-foreground")}>{item.name}</Text>
                      <Text className="text-caption text-foreground-secondary">{`${dates} ${item.memberCount}명 담은 장소 ${item.placeCount}곳`}</Text>
                    </View>
                  </Pressable>
                );
              })}
            </RadioGroup>
          )}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={onCreateRoom ? "새 여행방 만들기" : "새 여행방 만들기, 준비 중"}
            accessibilityHint={onCreateRoom ? "고른 장소를 담은 채로 새 여행방을 만들어요" : undefined}
            accessibilityState={{ disabled: !canCreateRoom }}
            disabled={!canCreateRoom}
            onPress={onCreateRoom}
            className={cn("flex-row items-center gap-3 rounded-md border border-dashed border-border p-4", canCreateRoom ? "active:bg-muted" : "opacity-50")}
            testID="room-sheet.new-room"
          >
            <View className="items-center justify-center rounded-full bg-brand-muted" style={{ width: 22, height: 22 }}>
              <Icon as={Plus} size={16} className="text-primary" />
            </View>
            <View className="flex-1 gap-1">
              <Text className="text-label text-primary">새 여행방 만들기</Text>
              <Text className="text-caption text-foreground-secondary">고른 장소를 담은 채로 만들어요</Text>
            </View>
            <Icon as={ChevronRight} size={20} className="text-icon" />
          </Pressable>
        </View>
        {placeCount === 0 && (
          <Text className="text-caption text-foreground-secondary" testID="room-sheet.unavailable">이 장소는 정보가 부족해 담을 수 없어요.</Text>
        )}
        {addErrorMessage !== null && (
          <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="text-caption text-destructive">{addErrorMessage}</Text>
        )}
        <Button
          size="lg"
          disabled={!room || placeCount === 0}
          loading={isAdding}
          loadingLabel="담는 중"
          onPress={submit}
          className="mb-2 w-full"
          accessibilityLabel={`선택한 ${placeCount}곳을 이 여행방에 담기`}
          testID="room-sheet.confirm"
        >
          <Text>{`${placeCount}곳 담기`}</Text>
        </Button>
      </View>
    </BottomSheet>
  );
}
