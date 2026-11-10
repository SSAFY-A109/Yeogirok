import { router, usePreventRemove } from "expo-router";
import { useRef, useState } from "react";
import { KeyboardAvoidingView, Platform, ScrollView, View } from "react-native";

import { isApiError } from "@/api/errors";
import { createIdempotencyKey } from "@/api/idempotency";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { MediaPhoto } from "@/components/ui/media-photo";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useAddRoomPlaces, useCreateRoom, useImportCoursePlaces } from "@/features/room/api/queries";
import { isValidRoomName, RoomNameField, TransportModeField, TripDateField } from "@/features/room/components/room-form";
import type { TripDateRange } from "@/features/room/components/trip-date-sheet";
import { getCourseImportErrorMessage, getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import type { AddRoomPlaceItemDto, PlaceRefDto, TransportModeDto } from "@/features/room/model";

/** 날짜 범위가 잘못됐다는 공통 오류 code. 날짜 필드 아래에 보여 준다. */
const DATE_RANGE_ERROR_CODE = "C012";

/** Figma W06에서 처음 선택된 이동 수단. */
const DEFAULT_TRANSPORT_MODE: TransportModeDto = "CAR";

/** 함께 담을 장소 카드에 보여 줄 사진 수(Figma W06 진입 1:5941). */
const MAX_CARRY_OVER_THUMBS = 3;

/** 담을 여행방 선택(W05)에서 넘겨받은 장소. 이름은 화면 표시용이다. */
export type CarryOverPlaces = { placeNames: string | null } & (
  | { kind: "search"; placeRefs: PlaceRefDto[] }
  | { kind: "course"; recordId: number; visitIds: number[] }
);

type NewRoomScreenProps = {
  carryOver: CarryOverPlaces | null;
  invalidCourseImport: boolean;
};

function goToRoom(roomId: number, mode: "push" | "replace") {
  router[mode]({ pathname: "/rooms/[roomId]", params: { roomId: String(roomId) } });
}

/**
 * 새 여행방 만들기(Figma W06, 날짜는 W06-C 시트). 만들면 여행방 상세로 바꿔 이동한다.
 * W05에서 장소를 고른 채 들어오면(Figma 진입 W06 1:5914) 만든 뒤 그 장소를 담고, 담기 결과를 토스트로 알리며 원래 화면으로 돌아간다.
 */
export function NewRoomScreen({ carryOver, invalidCourseImport }: NewRoomScreenProps) {
  const createRoom = useCreateRoom();
  const addRoomPlaces = useAddRoomPlaces();
  const importPlaces = useImportCoursePlaces();
  const toast = useToast();
  const pending = useRef<{ fingerprint: string; idempotencyKey: string } | null>(null);
  const [name, setName] = useState("");
  const [dateRange, setDateRange] = useState<TripDateRange | null>(null);
  const [transportMode, setTransportMode] = useState<TransportModeDto>(DEFAULT_TRANSPORT_MODE);

  const saveError = createRoom.error;
  const isDateError = isApiError(saveError) && saveError.code === DATE_RANGE_ERROR_CODE;
  const errorMessage = saveError === null ? null : getRoomErrorMessage(saveError, ROOM_ERROR_FALLBACK.save);
  const isSaving = createRoom.isPending || addRoomPlaces.isPending || importPlaces.isPending;

  const allowNavigation = usePreventRemove(isSaving, () => {
    // 방 생성과 장소 담기가 끝날 때까지 뒤로 가기를 막는다.
  });

  const clearSaveError = () => {
    if (createRoom.error !== null) {
      createRoom.reset();
    }
  };

  /** 담기 결과와 관계없이 이미 만들어진 여행방을 안내한다. */
  const carryPlacesInto = (roomId: number, roomName: string, places: CarryOverPlaces) => {
    const showCarryOverFailure = (message: string) => {
      toast.show({ message: `여행방은 만들었어요. ${message}` });
      allowNavigation();
      goToRoom(roomId, "replace");
    };

    const showCarryOverSuccess = (count: number) => {
      if (count === 0) {
        showCarryOverFailure("담긴 장소가 없어요. 여행방에서 확인해 주세요.");
        return;
      }

      toast.show({
        message: `${roomName}에 ${count}곳을 담았어요`,
        action: {
          label: "보러 가기",

          onPress: () => {
            goToRoom(roomId, "push");
          },
        },
      });

      allowNavigation();

      if (router.canGoBack()) {
        router.back();
      } else {
        goToRoom(roomId, "replace");
      }
    };

    if (places.kind === "course") {
      importPlaces.mutate({
        roomId,
        request: { recordId: places.recordId, visitIds: places.visitIds },
        idempotencyKey: createIdempotencyKey(),
      }, {
        onSuccess: showCarryOverSuccess,

        onError: (error) => {
          showCarryOverFailure(getCourseImportErrorMessage(error));
        },
      });
      return;
    }

    const items: AddRoomPlaceItemDto[] = places.placeRefs.map((placeRef) => {
      return { placeRef, sourceType: "SEARCH" };
    });

    addRoomPlaces.mutate(
      { roomId, items, idempotencyKey: createIdempotencyKey() },
      {
        onSuccess: ({ places: addedPlaces }) => {
          showCarryOverSuccess(addedPlaces.length);
        },

        onError: () => {
          showCarryOverFailure("장소를 담지 못했어요. 여행방에서 다시 담아 주세요.");
        },
      },
    );
  };

  const submit = () => {
    if (!isValidRoomName(name) || isSaving || invalidCourseImport) {
      return;
    }

    const values = {
      name: name.trim(),
      startDate: dateRange?.startDate ?? null,
      endDate: dateRange?.endDate ?? null,
      transportMode,
    };
    const fingerprint = JSON.stringify(values);

    if (pending.current?.fingerprint !== fingerprint) {
      pending.current = { fingerprint, idempotencyKey: createIdempotencyKey() };
    }

    createRoom.mutate(
      { values, idempotencyKey: pending.current.idempotencyKey },
      {
        onSuccess: (roomId) => {
          if (carryOver === null) {
            allowNavigation();
            goToRoom(roomId, "replace");
            return;
          }

          carryPlacesInto(roomId, values.name, carryOver);
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
            error={isDateError ? "여행 날짜를 다시 확인해 주세요." : undefined}
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
          {invalidCourseImport && (
            <Text accessibilityRole="alert" className="w-full text-caption text-destructive">
              코스로 돌아가 담을 장소를 다시 골라 주세요.
            </Text>
          )}
          {errorMessage !== null && !isDateError ? (
            <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="w-full text-caption text-destructive" testID="new-room.error">
              {errorMessage}
            </Text>
          ) : null}
          <Button
            size="lg"
            className="w-full"
            disabled={!isValidRoomName(name) || invalidCourseImport}
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
  const count = carryOver.kind === "course" ? carryOver.visitIds.length : carryOver.placeRefs.length;
  const thumbnailCount = Math.min(count, MAX_CARRY_OVER_THUMBS);
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
          {Array.from({ length: thumbnailCount }, (_, index) => {
            return <MediaPhoto key={index} uri={null} className="size-14" />;
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
