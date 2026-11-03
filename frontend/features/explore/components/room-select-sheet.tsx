import { useState, type ReactNode } from "react";
import { Pressable, View } from "react-native";
import { ChevronRight, Plus } from "lucide-react-native";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { cn } from "@/lib/utils";
import { useAddPlacesToRoom, useRooms } from "../api/queries";
import type { AddPlacesResult } from "../model";
import { InlineMessage } from "./query-states";

type RoomSelectSheetProps = {
  visible: boolean;
  placeNames: string[];
  placeIds: string[];
  onClose: () => void;
  onAdded: (result: AddPlacesResult) => void;
  /** 제목과 여행방 목록 사이에 보여 줄 내용(예: 장소 위치 지도). */
  children?: ReactNode;
};

export function RoomSelectSheet({ visible, placeNames, placeIds, onClose, onAdded, children }: RoomSelectSheetProps) {
  const rooms = useRooms(visible);
  const add = useAddPlacesToRoom();
  const [picked, setPicked] = useState<string>();
  const roomId = picked ?? rooms.data?.[0]?.id;

  function submit() {
    if (!roomId || add.isPending) {
      return;
    }

    add.mutate({ roomId, placeIds }, {
      onSuccess: (result) => {
        onAdded(result);
      },
    });
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
          {rooms.data && (
            <RadioGroup value={roomId ?? ""} onValueChange={setPicked} className="gap-2">
              {rooms.data.map((room) => {
                const selected = room.id === roomId;

                return (
                  <Pressable
                    key={room.id}
                    accessibilityRole="radio"
                    accessibilityLabel={`${room.name}, ${room.dates}, ${room.memberCount}명, 담은 장소 ${room.placeCount}곳`}
                    accessibilityState={{ checked: selected }}
                    onPress={() => {
                      setPicked(room.id);
                    }}
                    className={cn("flex-row items-center gap-3 rounded-md p-4", selected ? "border-2 border-ring bg-accent" : "border border-border bg-card")}
                    testID={`room-sheet.room.${room.id}`}
                  >
                    <RadioGroupItem value={room.id} accessible={false} />
                    <View className="flex-1 gap-1">
                      <Text className={cn("text-label", selected ? "text-primary" : "text-foreground")}>{room.name}</Text>
                      <Text className="text-caption text-foreground-secondary">{`${room.dates} ${room.memberCount}명 담은 장소 ${room.placeCount}곳`}</Text>
                    </View>
                  </Pressable>
                );
              })}
              <View
                accessibilityRole="button"
                accessibilityLabel="새 여행방 만들기, 준비 중"
                accessibilityState={{ disabled: true }}
                className="flex-row items-center gap-3 rounded-md border border-dashed border-border p-4 opacity-50"
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
              </View>
            </RadioGroup>
          )}
        </View>
        {add.isError && (
          <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="text-caption text-destructive">장소를 담지 못했어요. 다시 시도해 주세요.</Text>
        )}
        <Button
          size="lg"
          disabled={!roomId}
          loading={add.isPending}
          loadingLabel="담는 중"
          onPress={submit}
          className="mb-2 w-full"
          accessibilityLabel={`선택한 ${placeIds.length}곳을 이 여행방에 담기`}
          testID="room-sheet.confirm"
        >
          <Text>{`${placeIds.length}곳 담기`}</Text>
        </Button>
      </View>
    </BottomSheet>
  );
}
