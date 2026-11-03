import { router } from "expo-router";
import { useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView } from "react-native";

import { isApiError } from "@/api/errors";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Text } from "@/components/ui/text";
import { useCreateRoom } from "@/features/room/api/queries";
import { isValidRoomName, RoomNameField, TransportModeField, TripDateField } from "@/features/room/components/room-form";
import type { TripDateRange } from "@/features/room/components/trip-date-sheet";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import type { TransportModeDto } from "@/features/room/model";

/** 날짜 범위가 잘못됐다는 공통 오류 code. 날짜 필드 아래에 보여 준다. */
const DATE_RANGE_ERROR_CODE = "C016";

/** Figma W06에서 처음 선택된 이동 수단. */
const DEFAULT_TRANSPORT_MODE: TransportModeDto = "CAR";

/**
 * 새 여행방 만들기(Figma W06, 날짜는 W06-C 시트). 만들면 여행방 상세로 바꿔 이동한다.
 * 코스에서 장소를 담다가 들어오는 진입(고른 장소 카드)은 코스 화면이 없어 아직 다루지 않는다.
 */
export function NewRoomScreen() {
  const createRoom = useCreateRoom();
  const [name, setName] = useState("");
  const [dateRange, setDateRange] = useState<TripDateRange | null>(null);
  const [transportMode, setTransportMode] = useState<TransportModeDto>(DEFAULT_TRANSPORT_MODE);

  const saveError = createRoom.error;
  const isDateError = isApiError(saveError) && saveError.code === DATE_RANGE_ERROR_CODE;
  const errorMessage = saveError === null ? null : getRoomErrorMessage(saveError, ROOM_ERROR_FALLBACK.save);

  const clearSaveError = () => {
    if (createRoom.error !== null) {
      createRoom.reset();
    }
  };

  const submit = () => {
    if (!isValidRoomName(name) || createRoom.isPending) {
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
          router.replace({ pathname: "/rooms/[roomId]", params: { roomId: String(room.id) } });
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
            loading={createRoom.isPending}
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
