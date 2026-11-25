import { router } from "expo-router";
import { LogOut } from "lucide-react-native";
import { useState } from "react";
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, View } from "react-native";

import { isApiError } from "@/api/errors";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useRoom, useUpdateRoom } from "@/features/room/api/queries";
import { LeaveRoomDialog } from "@/features/room/components/leave-room-dialog";
import { isValidRoomName, RoomNameField, toTripDateRange, TransportModeField, TripDateField } from "@/features/room/components/room-form";
import { getScheduleImpact, ScheduleImpactDialog, type ScheduleImpact } from "@/features/room/components/schedule-impact-dialog";
import type { TripDateRange } from "@/features/room/components/trip-date-sheet";
import { getRoomErrorMessage, ROOM_DATE_RANGE_CODE, ROOM_DATES_IN_USE_CODE, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import type { RoomChanges, RoomDetail, TransportMode } from "@/features/room/model";

const HTTP_NOT_FOUND = 404;

/** 날짜 필드 아래에 보여 줄 오류 code. 날짜 범위·순서 오류와, 일정이 있는 방의 날짜를 비우려 한 경우다. */
const DATE_FIELD_ERROR_CODES: readonly string[] = [ROOM_DATE_RANGE_CODE, ROOM_DATES_IN_USE_CODE];

/** 로딩 스켈레톤에서 이름·날짜 필드 자리 수. */
const SKELETON_FIELD_COUNT = 2;

type RoomFormValues = { name: string; dateRange: TripDateRange | null; transportMode: TransportMode };

/** 바뀐 항목만 담은 수정 요청. 출발일과 돌아오는 날은 함께 정하거나 비우므로 둘 중 하나라도 바뀌면 함께 보낸다. */
function getRoomChanges(room: RoomDetail, { name, dateRange, transportMode }: RoomFormValues): RoomChanges {
  const request: RoomChanges = {};
  const trimmedName = name.trim();
  const startDate = dateRange?.startDate ?? null;
  const endDate = dateRange?.endDate ?? null;

  if (trimmedName !== room.name) {
    request.name = trimmedName;
  }

  if (startDate !== room.startDate || endDate !== room.endDate) {
    request.startDate = startDate;
    request.endDate = endDate;
  }

  if (transportMode !== room.transportMode && transportMode !== "UNKNOWN") {
    request.transportMode = transportMode;
  }

  return request;
}

/** 이전 화면(여행방 상세)으로 돌아간다. 돌아갈 화면이 없으면(딥링크 진입) 상세로 바꿔 이동한다. */
function returnToRoom(roomId: number) {
  if (router.canGoBack()) {
    router.back();
    return;
  }

  router.replace({ pathname: "/rooms/[roomId]", params: { roomId: String(roomId) } });
}

/**
 * 여행방 정보 수정(Figma W06-E). 일정이 있을 때 날짜나 이동 수단을 바꾸면 서버 미리보기(API-023 `previewOnly`)로
 * 일정에서 빠질 장소를 받아 저장 전에 W06-E2로 묻고, 맨 아래 "여행방 나가기"는 W06-L(혼자 남았으면 W06-L2)을 연다.
 * 참여자는 모두 같은 권한이라(팀 확정 정책) 누구나 고칠 수 있다.
 * `roomId`가 `null`이면 여행방을 찾을 수 없다는 안내를 보여 준다.
 */
export function EditRoomScreen({ roomId }: { roomId: number | null }) {
  return (
    <Screen>
      <ScreenHeader title="여행방 정보 수정" testID="edit-room.header" />
      {roomId === null ? <EditRoomNotFound /> : <EditRoomContent roomId={roomId} />}
    </Screen>
  );
}

function EditRoomContent({ roomId }: { roomId: number }) {
  const room = useRoom(roomId);

  if (room.isPending) {
    return <EditRoomSkeleton />;
  }

  if (room.isError) {
    if (isApiError(room.error) && room.error.status === HTTP_NOT_FOUND) {
      return <EditRoomNotFound />;
    }

    return (
      <EditRoomError
        message={getRoomErrorMessage(room.error, ROOM_ERROR_FALLBACK.load)}
        retrying={room.isFetching}
        onRetry={() => {
          void room.refetch();
        }}
      />
    );
  }

  return <EditRoomForm key={room.data.id} room={room.data} />;
}

// #region 폼

function EditRoomForm({ room }: { room: RoomDetail }) {
  const toast = useToast();
  const updateRoom = useUpdateRoom();
  const previewRoom = useUpdateRoom();
  const [name, setName] = useState(room.name);
  const [dateRange, setDateRange] = useState<TripDateRange | null>(toTripDateRange(room));
  const [transportMode, setTransportMode] = useState<TransportMode>(room.transportMode);
  const [pendingImpact, setPendingImpact] = useState<ScheduleImpact | null>(null);
  const [leaveOpen, setLeaveOpen] = useState(false);

  const changes = getRoomChanges(room, { name, dateRange, transportMode });
  const hasChanges = Object.keys(changes).length > 0;
  const datesChanged = "startDate" in changes;
  const transportChanged = "transportMode" in changes;

  const needsSchedulePreview = room.hasSchedule && (datesChanged || transportChanged);
  const canSave = hasChanges && isValidRoomName(name);

  const saveError = updateRoom.error ?? previewRoom.error;
  const isDateError = isApiError(saveError) && DATE_FIELD_ERROR_CODES.includes(saveError.code);
  const errorMessage = saveError === null ? null : getRoomErrorMessage(saveError, ROOM_ERROR_FALLBACK.save);

  const clearSaveError = () => {
    if (updateRoom.error !== null) {
      updateRoom.reset();
    }

    if (previewRoom.error !== null) {
      previewRoom.reset();
    }
  };

  const save = () => {
    updateRoom.mutate(
      { roomId: room.id, changes },
      {
        onSuccess: () => {
          setPendingImpact(null);
          returnToRoom(room.id);
          toast.show({ message: "여행방 정보를 바꿨어요" });
        },

        onError: () => {
          setPendingImpact(null);
        },
      },
    );
  };

  const handleSavePress = () => {
    if (!canSave || updateRoom.isPending || previewRoom.isPending) {
      return;
    }

    if (!needsSchedulePreview) {
      save();
      return;
    }

    updateRoom.reset();

    previewRoom.mutate(
      { roomId: room.id, changes, previewOnly: true },
      {
        onSuccess: ({ affectedItems }) => {
          const impact = getScheduleImpact({ affectedItems, previousRange: toTripDateRange(room), nextRange: dateRange, transportChanged });

          if (impact === null) {
            save();
            return;
          }

          setPendingImpact(impact);
        },
      },
    );
  };

  return (
    <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : "height"} className="flex-1">
      <ScrollView className="flex-1" contentContainerClassName="gap-6 px-5 pb-6 pt-4" keyboardShouldPersistTaps="handled">
        <RoomNameField
          value={name}
          onChangeText={(value) => {
            setName(value);
            clearSaveError();
          }}
          testID="edit-room"
        />
        <TripDateField
          value={dateRange}
          onChange={(value) => {
            setDateRange(value);
            clearSaveError();
          }}
          help="날짜를 줄이면 빠지는 날의 일정은 후보 장소로 돌아가요"
          error={isDateError && errorMessage !== null ? errorMessage : undefined}
          testID="edit-room"
        />
        <TransportModeField
          value={transportMode}
          onChange={(value) => {
            setTransportMode(value);
            clearSaveError();
          }}
          help="바꾸면 일정의 이동 시간을 다시 계산해요"
          testID="edit-room"
        />
        <LeaveRoomButton
          onPress={() => {
            setLeaveOpen(true);
          }}
        />
      </ScrollView>
      <BottomCta>
        {errorMessage !== null && !isDateError ? (
          <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="w-full text-caption text-destructive" testID="edit-room.error">
            {errorMessage}
          </Text>
        ) : null}
        <Button
          size="lg"
          className="w-full"
          disabled={!canSave}
          loading={updateRoom.isPending || previewRoom.isPending}
          loadingLabel={previewRoom.isPending ? "일정 확인 중" : "저장하는 중"}
          onPress={handleSavePress}
          testID="edit-room.save"
        >
          <Text>저장하기</Text>
        </Button>
      </BottomCta>
      <ScheduleImpactDialog
        impact={pendingImpact}
        onCancel={() => {
          setPendingImpact(null);
        }}
        onConfirm={save}
        loading={updateRoom.isPending}
      />
      <LeaveRoomDialog room={room} open={leaveOpen} onOpenChange={setLeaveOpen} />
    </KeyboardAvoidingView>
  );
}

function LeaveRoomButton({ onPress }: { onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="button"
      className="min-h-touch flex-row items-center gap-2 border-t border-border pb-2 pt-5 active:bg-muted"
      onPress={onPress}
      testID="edit-room.leave"
    >
      <Icon as={LogOut} size={20} className="text-destructive" />
      <Text className="text-label text-destructive">여행방 나가기</Text>
    </Pressable>
  );
}

// #endregion

// #region 상태

function EditRoomSkeleton() {
  return (
    <View accessible accessibilityLabel="여행방 정보를 불러오는 중" className="flex-1 gap-6 px-5 pt-4" testID="edit-room.loading">
      {Array.from({ length: SKELETON_FIELD_COUNT }, (_, index) => {
        return (
          <View key={index} className="gap-2">
            <Skeleton className="h-5 w-20" />
            <Skeleton className="h-input w-full rounded-sm" />
            <Skeleton className="h-4 w-skeleton-line" />
          </View>
        );
      })}
      <View className="gap-2">
        <Skeleton className="h-5 w-16" />
        <View className="flex-row gap-3">
          <Skeleton className="h-14 flex-1" />
          <Skeleton className="h-14 flex-1" />
        </View>
        <Skeleton className="h-4 w-skeleton-line" />
      </View>
    </View>
  );
}

function EditRoomError({ message, retrying, onRetry }: { message: string; retrying: boolean; onRetry: () => void }) {
  return (
    <View className="flex-1 items-center justify-center gap-4 px-5" testID="edit-room.error-state">
      <Text accessibilityRole="alert" className="text-center text-body-sm text-foreground-secondary">
        {message}
      </Text>
      <Button variant="outline" loading={retrying} loadingLabel="불러오는 중" onPress={onRetry} testID="edit-room.retry">
        <Text>다시 시도</Text>
      </Button>
    </View>
  );
}

function EditRoomNotFound() {
  return (
    <View className="flex-1 items-center justify-center gap-4 px-5" testID="edit-room.not-found">
      <Text accessibilityRole="alert" className="text-center text-h3 text-foreground">
        여행방을 찾을 수 없어요
      </Text>
      <Button
        variant="outline"
        onPress={() => {
          if (router.canGoBack()) {
            router.back();
            return;
          }

          router.dismissTo("/rooms");
        }}
        testID="edit-room.not-found-back"
      >
        <Text>돌아가기</Text>
      </Button>
    </View>
  );
}

// #endregion
