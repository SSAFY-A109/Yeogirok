import { router } from "expo-router";
import { useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, View } from "react-native";

import { isApiError } from "@/api/errors";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { MediaPhoto } from "@/components/ui/media-photo";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useAddRoomPlaces, useCreateRoom } from "@/features/room/api/queries";
import { isValidRoomName, RoomNameField, TransportModeField, TripDateField } from "@/features/room/components/room-form";
import type { TripDateRange } from "@/features/room/components/trip-date-sheet";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import type { RoomDetail, TransportModeDto } from "@/features/room/model";

/** 날짜 범위가 잘못됐다는 공통 오류 code. 날짜 필드 아래에 보여 준다. */
const DATE_RANGE_ERROR_CODE = "C016";

/** Figma W06에서 처음 선택된 이동 수단. */
const DEFAULT_TRANSPORT_MODE: TransportModeDto = "CAR";

/** 함께 담을 장소 카드에 보여 줄 사진 수(Figma W06 진입 1:5941). */
const MAX_CARRY_OVER_THUMBS = 3;

/** 담을 여행방 선택(W05)에서 넘겨받은 장소. 이름은 화면 표시용이다. */
export type CarryOverPlaces = { placeIds: number[]; placeNames: string | null };

type NewRoomScreenProps = {
  carryOver: CarryOverPlaces | null;
};

function goToRoom(roomId: number, mode: "push" | "replace") {
  router[mode]({ pathname: "/rooms/[roomId]", params: { roomId: String(roomId) } });
}

/**
 * 새 여행방 만들기(Figma W06, 날짜는 W06-C 시트). 만들면 여행방 상세로 바꿔 이동한다.
 * W05에서 장소를 고른 채 들어오면(Figma 진입 W06 1:5914) 만든 뒤 그 장소를 담고, 담기 결과를 토스트로 알리며 원래 화면으로 돌아간다.
 */
export function NewRoomScreen({ carryOver }: NewRoomScreenProps) {
  const createRoom = useCreateRoom();
  const addRoomPlaces = useAddRoomPlaces();
  const toast = useToast();
  const [name, setName] = useState("");
  const [dateRange, setDateRange] = useState<TripDateRange | null>(null);
  const [transportMode, setTransportMode] = useState<TransportModeDto>(DEFAULT_TRANSPORT_MODE);

  const saveError = createRoom.error;
  const isDateError = isApiError(saveError) && saveError.code === DATE_RANGE_ERROR_CODE;
  const errorMessage = saveError === null ? null : getRoomErrorMessage(saveError, ROOM_ERROR_FALLBACK.save);
  const isSaving = createRoom.isPending || addRoomPlaces.isPending;

  const clearSaveError = () => {
    if (createRoom.error !== null) {
      createRoom.reset();
    }
  };

  /** 여행방은 이미 만들어졌으므로 담기에 실패해도 다시 만들지 않고 상세로 보내 거기서 다시 담게 한다. */
  const carryPlacesInto = (room: RoomDetail, placeIds: number[]) => {
    const showCarryOverFailure = () => {
      toast.show({ message: "여행방은 만들었지만 장소를 담지 못했어요. 여행방에서 다시 담아 주세요." });
      goToRoom(room.id, "replace");
    };

    addRoomPlaces.mutate(
      { roomId: room.id, request: { placeIds, source: "SEARCH", recommendationRequestId: null } },
      {
        onSuccess: (addedPlaces) => {
          if (addedPlaces.length === 0) {
            showCarryOverFailure();
            return;
          }

          toast.show({
            message: `${room.name}에 ${addedPlaces.length}곳을 담았어요`,
            action: {
              label: "보러 가기",

              onPress: () => {
                goToRoom(room.id, "push");
              },
            },
          });

          if (router.canGoBack()) {
            router.back();
          } else {
            goToRoom(room.id, "replace");
          }
        },

        onError: showCarryOverFailure,
      },
    );
  };

  const submit = () => {
    if (!isValidRoomName(name) || isSaving) {
      return;
    }

    createRoom.mutate(
      {
        name: name.trim(),
        startDate: dateRange?.startDate ?? null,
        endDate: dateRange?.endDate ?? null,
        transportMode,
      },
      {
        onSuccess: (room) => {
          if (carryOver === null) {
            goToRoom(room.id, "replace");
            return;
          }

          carryPlacesInto(room, carryOver.placeIds);
        },
      },
    );
  };

  return (
    <Screen>
      <ScreenHeader title="새 여행방" testID="new-room.header" />
      <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"} className="flex-1">
        <ScrollView className="flex-1" contentContainerClassName="gap-6 px-5 pb-6 pt-4" keyboardShouldPersistTaps="handled">
          <RoomNameField
            value={name}
            onChangeText={(value) => {
              setName(value);
              clearSaveError();
            }}
            testID="new-room"
          />
          <TripDateField
            value={dateRange}
            onChange={(value) => {
              setDateRange(value);
              clearSaveError();
            }}
            help="아직 모르면 비워 두고 나중에 정해도 돼요"
            error={isDateError && errorMessage !== null ? errorMessage : undefined}
            testID="new-room"
          />
          <TransportModeField
            value={transportMode}
            onChange={(value) => {
              setTransportMode(value);
              clearSaveError();
            }}
            help="AI 일정 초안의 이동 시간 계산에 써요"
            testID="new-room"
          />
          {carryOver !== null ? <CarryOverPlacesField carryOver={carryOver} /> : null}
        </ScrollView>
        <BottomCta>
          {errorMessage !== null && !isDateError ? (
            <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="w-full text-caption text-destructive" testID="new-room.error">
              {errorMessage}
            </Text>
          ) : null}
          <Button
            size="lg"
            className="w-full"
            disabled={!isValidRoomName(name)}
            loading={isSaving}
            loadingLabel="만드는 중"
            onPress={submit}
            testID="new-room.submit"
          >
            <Text>여행방 만들기</Text>
          </Button>
        </BottomCta>
      </KeyboardAvoidingView>
    </Screen>
  );
}

/** 함께 담을 장소(Figma 진입 W06 `Field / 함께 담을 장소` 1:5939). 사진은 아직 받지 않아 자리만 보인다. */
function CarryOverPlacesField({ carryOver }: { carryOver: CarryOverPlaces }) {
  const count = carryOver.placeIds.length;
  const thumbKeys = carryOver.placeIds.slice(0, MAX_CARRY_OVER_THUMBS);
  const summary = `고른 ${count}곳을 담은 채로 만들어요`;

  return (
    <View className="gap-2" testID="new-room.carry-over">
      <Text className="text-label text-foreground">함께 담을 장소</Text>
      <View
        accessible
        accessibilityLabel={carryOver.placeNames === null ? summary : `${summary}. ${carryOver.placeNames}`}
        className="gap-3 rounded-lg bg-muted p-4"
      >
        <View className="flex-row gap-2">
          {thumbKeys.map((placeId) => {
            return <MediaPhoto key={placeId} uri={null} className="size-14" />;
          })}
        </View>
        <Text className="text-label text-foreground">{summary}</Text>
        {carryOver.placeNames !== null ? (
          <Text numberOfLines={2} className="text-caption text-foreground-secondary">{carryOver.placeNames}</Text>
        ) : null}
      </View>
    </View>
  );
}
