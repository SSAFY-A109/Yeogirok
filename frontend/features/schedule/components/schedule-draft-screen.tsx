import { Redirect, router } from "expo-router";
import type { ReactNode } from "react";
import { View } from "react-native";

import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useAllRoomPlaces, useRoom } from "@/features/room/api/queries";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import { useSavedScheduleDraft, useScheduleDraft } from "@/features/schedule/api/queries";
import { ScheduleEditor } from "@/features/schedule/components/schedule-editor";
import { ScheduleProcessing } from "@/features/schedule/components/schedule-processing";
import { getDraftFailureMessage, getScheduleErrorMessage, isDraftNotFound, SCHEDULE_ERROR_FALLBACK } from "@/features/schedule/errors";
import { toPlaceLookup } from "@/features/schedule/model";
import { goToScheduleConditions } from "@/features/schedule/navigation";
import { KEEP_SAVED_PARAM, type ScheduleSource } from "@/features/schedule/route-params";

const TITLE = "일정 초안";

function goBack() {
  router.back();
}

type ScheduleDraftScreenProps = {
  /** 검증한 라우트 파라미터. 잘못된 id면 `null`. */
  roomId: number | null;
  /** 보여 줄 일정. 잘못된 초안 키면 `null`. */
  source: ScheduleSource | null;
};

/**
 * 일정 초안(Figma W14, TRP-08·09·COM-05). 만든 초안을 검토·조정하거나("AI로 일정 만들기"),
 * 저장된 일정을 이어받은 초안을 만들어 고친다("일정 조정하기", `draftKey` 없음). 저장하기 전에는 여행방 일정을 바꾸지 않는다.
 */
export function ScheduleDraftScreen({ roomId, source }: ScheduleDraftScreenProps) {
  if (roomId === null || source === null) {
    return <DraftNotFound />;
  }

  if (source.kind === "saved") {
    return <SavedScheduleAdjustment roomId={roomId} />;
  }

  return (
    <ScheduleDraftContent
      roomId={roomId}
      draftKey={source.draftKey}
      adjusting={false}

      onRecreate={() => {
        goToScheduleConditions(roomId);
      }}
    />
  );
}

/** "일정 조정하기". 저장된 일정을 이어받는 초안을 만들고(API-037 `fixedItems`) 같은 편집기로 고친다. */
function SavedScheduleAdjustment({ roomId }: { roomId: number }) {
  const startQuery = useSavedScheduleDraft(roomId);
  const start = startQuery.data;

  const restart = () => {
    void startQuery.refetch();
  };

  if (startQuery.isFetching) {
    return <DraftLoading />;
  }

  if (start === undefined) {
    return (
      <DraftMessage message={getScheduleErrorMessage(startQuery.error, SCHEDULE_ERROR_FALLBACK.draft)}>
        <Button variant="outline" onPress={restart} testID="schedule-draft.retry">
          <Text>다시 시도</Text>
        </Button>
      </DraftMessage>
    );
  }

  switch (start.kind) {
    case "DRAFT":
      return <ScheduleDraftContent roomId={roomId} draftKey={start.draftKey} adjusting onRecreate={restart} />;
    case "NEEDS_CONDITIONS":
      return <Redirect href={{ pathname: "/rooms/[roomId]/schedule/new", params: { roomId: String(roomId), keep: KEEP_SAVED_PARAM } }} />;
    case "NO_SCHEDULE":
      return (
        <DraftMessage message="아직 정한 일정이 없어요. AI로 일정 초안을 먼저 만들어 주세요.">
          <Button
            variant="outline"

            onPress={() => {
              goToScheduleConditions(roomId);
            }}

            testID="schedule-draft.create"
          >
            <Text>AI로 일정 만들기</Text>
          </Button>
        </DraftMessage>
      );
  }
}

type ScheduleDraftContentProps = {
  roomId: number;
  draftKey: string;
  /** 저장된 일정에서 시작한 초안인지. */
  adjusting: boolean;
  /** 이 초안을 버리고 다시 만든다. */
  onRecreate: () => void;
};

/**
 * 초안 상태에 따라 처리 중·실패·편집기를 보여 준다. 다시 계산하는 중에는 서버가 마지막 계산 결과를 함께 주므로
 * 처리 중 화면으로 바꾸지 않고 편집기를 그대로 둔다(고른 날·스크롤 유지).
 */
function ScheduleDraftContent({ roomId, draftKey, adjusting, onRecreate }: ScheduleDraftContentProps) {
  const draftQuery = useScheduleDraft(roomId, draftKey);
  const placesQuery = useAllRoomPlaces(roomId);
  const roomQuery = useRoom(roomId);
  const draft = draftQuery.data;

  const checkAgain = () => {
    void draftQuery.refetch();
  };

  if (draft === undefined) {
    if (!draftQuery.isError) {
      return <DraftLoading />;
    }

    if (isDraftNotFound(draftQuery.error)) {
      return <DraftNotFound />;
    }

    return (
      <DraftMessage message={getScheduleErrorMessage(draftQuery.error)}>
        <Button variant="outline" loading={draftQuery.isFetching} loadingLabel="불러오는 중" onPress={checkAgain} testID="schedule-draft.retry">
          <Text>다시 시도</Text>
        </Button>
      </DraftMessage>
    );
  }

  switch (draft.status) {
    case "PROCESSING":
      return (
        <DraftFrame>
          <ScheduleProcessing
            placeCount={roomQuery.data?.placeCount ?? null}
            checking={draftQuery.isFetching}
            connectionTrouble={draftQuery.isError}
            onCheckAgain={checkAgain}
          />
        </DraftFrame>
      );
    case "FAILED":
      return (
        <DraftMessage message={getDraftFailureMessage(draft.errorCode)}>
          <Button
            variant="outline"

            onPress={() => {
              goToScheduleConditions(roomId, { keepSaved: adjusting });
            }}

            testID="schedule-draft.retry-conditions"
          >
            <Text>조건 다시 정하기</Text>
          </Button>
        </DraftMessage>
      );
    case "UNKNOWN":
      return (
        <DraftMessage message="일정 초안 상태를 확인하지 못했어요. 잠시 후 다시 확인해 주세요.">
          <Button variant="outline" loading={draftQuery.isFetching} loadingLabel="확인하는 중" onPress={checkAgain} testID="schedule-draft.check-status">
            <Text>다시 확인</Text>
          </Button>
        </DraftMessage>
      );
    case "READY":
      break;
  }

  if (placesQuery.data === undefined) {
    if (!placesQuery.isError) {
      return <DraftLoading />;
    }

    return (
      <DraftMessage message={getRoomErrorMessage(placesQuery.error, ROOM_ERROR_FALLBACK.load)}>
        <Button
          variant="outline"
          loading={placesQuery.isFetching}
          loadingLabel="불러오는 중"

          onPress={() => {
            void placesQuery.refetch();
          }}

          testID="schedule-draft.places-retry"
        >
          <Text>다시 시도</Text>
        </Button>
      </DraftMessage>
    );
  }

  return (
    <ScheduleEditor
      key={draftKey}
      roomId={roomId}
      draftKey={draftKey}
      room={roomQuery.data}
      draft={draft}
      places={toPlaceLookup(placesQuery.data)}
      adjusting={adjusting}
      progress={{ checking: draftQuery.isFetching, connectionTrouble: draftQuery.isError, onCheckAgain: checkAgain }}
      onRecreate={onRecreate}
    />
  );
}

// #region 화면 틀

function DraftFrame({ children }: { children: ReactNode }) {
  return (
    <Screen>
      <ScreenHeader title={TITLE} onBack={goBack} testID="schedule-draft.header" />
      {children}
    </Screen>
  );
}

function DraftLoading() {
  return (
    <DraftFrame>
      <View accessibilityRole="progressbar" accessibilityLabel="일정을 불러오는 중" accessibilityState={{ busy: true }} className="gap-4 px-5 pt-4">
        <Skeleton className="h-8 w-56 rounded-xs" />
        <Skeleton className="h-4 w-72 rounded-xs" />
        <Skeleton className="h-14 w-full rounded-md" />
        <Skeleton className="h-40 w-full rounded-md" />
      </View>
    </DraftFrame>
  );
}

function DraftMessage({ message, children }: { message: string; children?: ReactNode }) {
  return (
    <DraftFrame>
      <View className="flex-1 items-center justify-center gap-4 px-5 pb-12" testID="schedule-draft.message">
        <Text className="text-center text-body-sm text-foreground-secondary">{message}</Text>
        {children}
      </View>
    </DraftFrame>
  );
}

function DraftNotFound() {
  return (
    <DraftMessage message="일정 초안을 찾을 수 없어요">
      <Button variant="outline" onPress={goBack} testID="schedule-draft.back">
        <Text>돌아가기</Text>
      </Button>
    </DraftMessage>
  );
}

// #endregion
