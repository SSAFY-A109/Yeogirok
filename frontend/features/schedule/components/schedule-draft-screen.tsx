import { router } from "expo-router";
import type { ReactNode } from "react";
import { View } from "react-native";

import { isApiError } from "@/api/errors";
import { Button } from "@/components/ui/button";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useRoom } from "@/features/room/api/queries";
import { useRoomSchedule, useScheduleDraft } from "@/features/schedule/api/queries";
import { ScheduleEditor, type EditableSchedule, type RecalculationProgress } from "@/features/schedule/components/schedule-editor";
import { ScheduleProcessing } from "@/features/schedule/components/schedule-processing";
import { getDraftFailureMessage, getScheduleErrorMessage } from "@/features/schedule/errors";
import type { RoomSchedule, ScheduleDraft } from "@/features/schedule/model";
import { goToScheduleConditions, showSchedule } from "@/features/schedule/navigation";
import type { ScheduleSource } from "@/features/schedule/route-params";

const TITLE = "일정 초안";
const NOT_FOUND_STATUS = 404;

function goBack() {
  router.back();
}

type ScheduleDraftScreenProps = {
  /** 검증한 라우트 파라미터. 잘못된 id면 `null`. */
  roomId: number | null;
  /** 보여 줄 일정. 잘못된 초안 id면 `null`. */
  source: ScheduleSource | null;
  /** 다시 계산하기 전에 보던 일정. 새 초안이 끝날 때까지 이 일정을 대신 보여 준다. */
  from: ScheduleSource | null;
};

/**
 * 일정 초안(Figma W14, TRP-08·09·COM-05). AI가 만든 초안을 보거나("AI로 일정 만들기"),
 * 저장된 일정에서 시작해 고친다("일정 조정하기", `draftId` 없음). 저장하기 전에는 여행방 일정을 바꾸지 않는다.
 */
export function ScheduleDraftScreen({ roomId, source, from }: ScheduleDraftScreenProps) {
  if (roomId === null || source === null) {
    return <DraftNotFound />;
  }

  return <ScheduleDraftContent roomId={roomId} source={source} from={from} />;
}

// #region 보여 줄 상태

type ScheduleView =
  | { kind: "loading" }
  | { kind: "not-found" }
  | { kind: "error"; error: unknown; retrying: boolean; retry: () => void }
  | { kind: "no-schedule" }
  | { kind: "processing"; checking: boolean; connectionTrouble: boolean; checkAgain: () => void }
  | { kind: "failed"; failureCode: string | null; from: ScheduleSource | null }
  | { kind: "unknown"; checking: boolean; checkAgain: () => void }
  | { kind: "ready"; editable: EditableSchedule; recalculation: RecalculationProgress | null };

function toSavedEditable(schedule: RoomSchedule | null | undefined): EditableSchedule | null {
  return schedule ? { schedule, source: { kind: "saved" }, baseVersion: schedule.version } : null;
}

function toDraftEditable(draft: ScheduleDraft | undefined): EditableSchedule | null {
  return draft?.status === "COMPLETED" ? { schedule: draft.schedule, source: { kind: "draft", draftId: draft.id }, baseVersion: draft.baseVersion } : null;
}

/**
 * 보여 줄 상태를 정한다. 다시 계산한 초안(`from`이 있음)이 처리 중이면 처리 중 화면 대신
 * 이전 일정을 그대로 두고 진행만 알려, 고치던 화면(고른 날·스크롤)이 유지되게 한다.
 */
function useScheduleView(roomId: number, source: ScheduleSource, from: ScheduleSource | null): ScheduleView {
  const savedQuery = useRoomSchedule(roomId, { enabled: source.kind === "saved" || from?.kind === "saved" });
  const draftQuery = useScheduleDraft(roomId, source.kind === "draft" ? source.draftId : null);
  const fromDraftQuery = useScheduleDraft(roomId, from?.kind === "draft" ? from.draftId : null);
  const savedEditable = toSavedEditable(savedQuery.data);

  if (source.kind === "saved") {
    if (savedQuery.data === null) {
      return { kind: "no-schedule" };
    }

    if (savedEditable !== null) {
      return { kind: "ready", editable: savedEditable, recalculation: null };
    }

    if (savedQuery.isError) {
      return {
        kind: "error",
        error: savedQuery.error,
        retrying: savedQuery.isFetching,

        retry: () => {
          void savedQuery.refetch();
        },
      };
    }

    return { kind: "loading" };
  }

  const draft = draftQuery.data;

  const checkAgain = () => {
    void draftQuery.refetch();
  };

  if (draft === undefined && draftQuery.isError) {
    return isApiError(draftQuery.error) && draftQuery.error.status === NOT_FOUND_STATUS
      ? { kind: "not-found" }
      : { kind: "error", error: draftQuery.error, retrying: draftQuery.isFetching, retry: checkAgain };
  }

  if (draft === undefined || draft.status === "PROCESSING") {
    const previous = from === null ? null : from.kind === "saved" ? savedEditable : toDraftEditable(fromDraftQuery.data);

    if (previous !== null) {
      return { kind: "ready", editable: previous, recalculation: { checking: draftQuery.isFetching, connectionTrouble: draftQuery.isError, onCheckAgain: checkAgain } };
    }

    return draft === undefined ? { kind: "loading" } : { kind: "processing", checking: draftQuery.isFetching, connectionTrouble: draftQuery.isError, checkAgain };
  }

  switch (draft.status) {
    case "FAILED":
      return { kind: "failed", failureCode: draft.failureCode, from };
    case "COMPLETED":
      return { kind: "ready", editable: { schedule: draft.schedule, source, baseVersion: draft.baseVersion }, recalculation: null };
    case "UNKNOWN":
      return { kind: "unknown", checking: draftQuery.isFetching, checkAgain };
  }
}

// #endregion

function ScheduleDraftContent({ roomId, source, from }: { roomId: number; source: ScheduleSource; from: ScheduleSource | null }) {
  const view = useScheduleView(roomId, source, from);
  const roomQuery = useRoom(roomId);

  switch (view.kind) {
    case "ready":
      return <ScheduleEditor roomId={roomId} room={roomQuery.data} editable={view.editable} recalculation={view.recalculation} />;
    case "loading":
      return <DraftLoading />;
    case "not-found":
      return <DraftNotFound />;
    case "error":
      return (
        <DraftMessage message={getScheduleErrorMessage(view.error)}>
          <Button variant="outline" loading={view.retrying} loadingLabel="불러오는 중" onPress={view.retry} testID="schedule-draft.retry">
            <Text>다시 시도</Text>
          </Button>
        </DraftMessage>
      );
    case "no-schedule":
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
    case "processing":
      return (
        <DraftFrame>
          <ScheduleProcessing
            placeCount={roomQuery.data?.placeCount ?? null}
            checking={view.checking}
            connectionTrouble={view.connectionTrouble}
            onCheckAgain={view.checkAgain}
          />
        </DraftFrame>
      );
    case "failed":
      return <DraftFailed roomId={roomId} failureCode={view.failureCode} from={view.from} />;
    case "unknown":
      return (
        <DraftMessage message="일정 초안 상태를 확인하지 못했어요. 잠시 후 다시 확인해 주세요.">
          <Button variant="outline" loading={view.checking} loadingLabel="확인하는 중" onPress={view.checkAgain} testID="schedule-draft.check-status">
            <Text>다시 확인</Text>
          </Button>
        </DraftMessage>
      );
  }
}

/** 초안 작업 실패. 다시 계산하다 실패했으면 고치던 일정으로, 처음 만들다 실패했으면 조건 화면으로 보낸다. */
function DraftFailed({ roomId, failureCode, from }: { roomId: number; failureCode: string | null; from: ScheduleSource | null }) {
  if (from !== null) {
    return (
      <DraftMessage message={getDraftFailureMessage(failureCode)}>
        <Button
          variant="outline"

          onPress={() => {
            showSchedule(from);
          }}

          testID="schedule-draft.back-to-previous"
        >
          <Text>고치던 일정으로 돌아가기</Text>
        </Button>
      </DraftMessage>
    );
  }

  return (
    <DraftMessage message={getDraftFailureMessage(failureCode)}>
      <Button
        variant="outline"

        onPress={() => {
          goToScheduleConditions(roomId);
        }}

        testID="schedule-draft.retry-conditions"
      >
        <Text>조건 다시 정하기</Text>
      </Button>
    </DraftMessage>
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
