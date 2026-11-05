import { router } from "expo-router";
import { ArrowLeftRight, ChevronDown, ChevronRight, ChevronUp, GripVertical, Hourglass, Info, TriangleAlert, type LucideIcon } from "lucide-react-native";
import { useRef, useState, type ReactNode } from "react";
import { Pressable, ScrollView, View } from "react-native";

import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { TRANSPORT_MODE_LABEL, type RoomDetail } from "@/features/room/model";
import { SCHEDULE_DRAFT_MAX_WAIT_MS, useApplyScheduleDraft, useCreateScheduleDraft, useRoomSchedule } from "@/features/schedule/api/queries";
import { DayChips, type DayChipItem } from "@/features/schedule/components/day-chips";
import { DayPickerSheet } from "@/features/schedule/components/day-picker-sheet";
import { MoveStopSheet, type MoveDayOption, type MoveStopTarget } from "@/features/schedule/components/move-stop-sheet";
import { ScheduleDayTimeline } from "@/features/schedule/components/schedule-day-timeline";
import { StayTimeSheet, type StayTarget } from "@/features/schedule/components/stay-time-sheet";
import { getScheduleErrorMessage, isScheduleConflict, SCHEDULE_ERROR_FALLBACK } from "@/features/schedule/errors";
import {
  formatDuration,
  getScheduleChanges,
  hasUnplacedStops,
  isSameLayoutOrder,
  moveStopToDay,
  moveStopWithinDay,
  removeStop,
  SCHEDULE_PACE_LABEL,
  setStopStay,
  toPaceRequest,
  toRecalculationRequest,
  toScheduleLayout,
  type ScheduleBody,
  type ScheduleDay,
  type ScheduleLayoutDay,
  type ScheduleStop,
  type UnplacedStop,
} from "@/features/schedule/model";
import { goToRoomSchedule, goToScheduleConditions, showSchedule } from "@/features/schedule/navigation";
import type { ScheduleSource } from "@/features/schedule/route-params";
import { useElapsedSeconds } from "@/hooks/use-elapsed-seconds";
import { useIdempotencyKeyFor } from "@/hooks/use-idempotency-key";
import { formatShortMonthDay, type LocalDate } from "@/lib/date";
import { withDirectionParticle, withObjectParticle } from "@/lib/korean";
import { cn } from "@/lib/utils";

const MS_PER_SECOND = 1_000;

/** 화면이 보여 주고 고치는 일정. 저장된 일정이면 `baseVersion`은 그 일정의 버전이다. */
export type EditableSchedule = { schedule: ScheduleBody; source: ScheduleSource; baseVersion: number | null };

/** 이 일정을 고쳐 다시 계산한 초안이 아직 처리 중일 때의 진행 확인 상태. */
export type RecalculationProgress = { checking: boolean; connectionTrouble: boolean; onCheckAgain: () => void };

type ScheduleEditorProps = {
  roomId: number;
  room: RoomDetail | undefined;
  editable: EditableSchedule;
  /** 다시 계산 중이면 진행 상태. 그동안 고치기와 저장을 막는다. */
  recalculation: RecalculationProgress | null;
};

/** 다시 계산할 뼈대와 끝난 뒤 되돌리기 토스트 문구. 문구가 없으면 토스트를 띄우지 않는다. */
type Recalculation = { layout: ScheduleLayoutDay[]; doneMessage: string | null };

/** 장소 카드와 넣지 못한 장소에서 하는 일. */
type DayActions = {
  openMove: (stop: ScheduleStop, day: ScheduleDay) => void;
  openStay: (stop: ScheduleStop) => void;
  startReorder: () => void;
  moveUnplaced: (stop: UnplacedStop, toDate: LocalDate) => void;
  openMoveUnplaced: (stop: UnplacedStop, day: ScheduleDay) => void;
};

function goBack() {
  router.back();
}

function dayLabelOf(index: number): string {
  return `${index + 1}일차`;
}

/**
 * 일정 초안 검토·조정(Figma W14·W14-M·W14-T, TRP-08·09).
 * 다른 날로 옮기기·빼기·머무는 시간은 고르면 바로, 순서 바꾸기는 다 바꾼 뒤 "다시 계산"으로 새 초안을 만든다(API-037).
 * 저장(API-039)하기 전에는 여행방 일정을 바꾸지 않는다.
 */
export function ScheduleEditor({ roomId, room, editable, recalculation }: ScheduleEditorProps) {
  const { schedule, source, baseVersion } = editable;
  const [selectedDate, setSelectedDate] = useState(schedule.days.at(0)?.date);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [reorderLayout, setReorderLayout] = useState<ScheduleLayoutDay[] | null>(null);
  const [moveTarget, setMoveTarget] = useState<MoveStopTarget | null>(null);
  const [stayTarget, setStayTarget] = useState<StayTarget | null>(null);
  const createDraft = useCreateScheduleDraft();
  const applyDraft = useApplyScheduleDraft();
  const keyFor = useIdempotencyKeyFor();
  const lastRecalculationRef = useRef<Recalculation | null>(null);
  const toast = useToast();

  const layout = toScheduleLayout(schedule);
  const recalculating = recalculation !== null || createDraft.isPending;
  const editingLocked = recalculating || applyDraft.isPending;

  const selectedIndex = Math.max(
    schedule.days.findIndex((day) => {
      return day.date === selectedDate;
    }),
    0,
  );

  const selectedDay = schedule.days.at(selectedIndex);
  const chipItems = toDayChipItems(schedule.days);

  const moveDays: MoveDayOption[] = schedule.days.map((day, index) => {
    return { date: day.date, label: dayLabelOf(index), stopCount: day.stops.length };
  });

  const reorderDay = reorderLayout?.find((day) => {
    return day.date === selectedDay?.date;
  });

  const labelOfDate = (date: LocalDate) => {
    return moveDays.find((day) => {
      return day.date === date;
    })?.label ?? "";
  };

  const recalculate = (run: Recalculation) => {
    lastRecalculationRef.current = run;
    applyDraft.reset();
    const request = toRecalculationRequest(toPaceRequest(schedule.pace), run.layout);

    createDraft.mutate(
      { roomId, request, idempotencyKey: keyFor(JSON.stringify(request)) },
      {
        onSuccess: ({ draftId }) => {
          setReorderLayout(null);
          showSchedule({ kind: "draft", draftId }, source);

          if (run.doneMessage !== null) {
            toast.show({
              message: run.doneMessage,

              action: {
                label: "되돌리기",

                onPress: () => {
                  showSchedule(source);
                },
              },
            });
          }
        },
      },
    );
  };

  const retryLastRecalculation = () => {
    if (lastRecalculationRef.current !== null) {
      recalculate(lastRecalculationRef.current);
    }
  };

  const moveStop = (target: MoveStopTarget, toDate: LocalDate) => {
    setMoveTarget(null);
    recalculate({ layout: moveStopToDay(layout, target.stopId, toDate), doneMessage: `${withDirectionParticle(labelOfDate(toDate))} 옮겼어요` });
  };

  const removeFromSchedule = (target: MoveStopTarget) => {
    setMoveTarget(null);
    recalculate({ layout: removeStop(layout, target.stopId), doneMessage: `${withObjectParticle(target.name)} 후보 장소로 돌려보냈어요` });
  };

  const changeStay = (target: StayTarget, stayMinutes: number) => {
    setStayTarget(null);
    recalculate({ layout: setStopStay(layout, target.stopId, stayMinutes), doneMessage: null });
  };

  const dayActions: DayActions = {
    openMove: (stop, day) => {
      setMoveTarget({
        stopId: stop.id,
        name: stop.name,
        fromDate: day.date,
        detail: `지금 ${labelOfDate(day.date)} ${stop.arrivalTime} – ${stop.departureTime}`,
        suggestedDate: null,
      });
    },

    openStay: (stop) => {
      setStayTarget({
        stopId: stop.id,
        name: stop.name,
        stayMinutes: stop.stayMinutes,
        defaultStayMinutes: stop.defaultStayMinutes,
        categoryName: stop.category?.name ?? null,
        detail: `지금 ${stop.arrivalTime} – ${stop.departureTime}`,
      });
    },

    startReorder: () => {
      setReorderLayout(layout);
    },

    moveUnplaced: (stop, toDate) => {
      recalculate({ layout: moveStopToDay(layout, stop.id, toDate), doneMessage: `${withDirectionParticle(labelOfDate(toDate))} 옮겼어요` });
    },

    openMoveUnplaced: (stop, day) => {
      setMoveTarget({
        stopId: stop.id,
        name: stop.name,
        fromDate: day.date,
        detail: `지금 ${labelOfDate(day.date)} · 시간 안에 넣지 못한 장소`,
        suggestedDate: stop.suggestedDate,
      });
    },
  };

  const save = () => {
    if (source.kind !== "draft") {
      return;
    }

    applyDraft.mutate(
      { roomId, draftId: source.draftId, baseVersion },
      {
        onSuccess: () => {
          toast.show({ message: "일정을 저장했어요" });
          goToRoomSchedule(roomId);
        },
      },
    );
  };

  const renderActions = () => {
    if (isScheduleConflict(applyDraft.error)) {
      return <ConflictActions roomId={roomId} />;
    }

    const recalculateError = createDraft.isError ? (
      <ErrorLine
        message={getScheduleErrorMessage(createDraft.error, SCHEDULE_ERROR_FALLBACK.recalculate)}
        onRetry={retryLastRecalculation}
        testID="schedule-draft.recalculate-error"
      />
    ) : null;

    if (reorderLayout !== null) {
      return (
        <>
          {recalculateError}
          <View className="w-full flex-row gap-2">
            <Button
              variant="outline"
              size="lg"
              className="flex-1"
              disabled={recalculating}

              onPress={() => {
                setReorderLayout(null);
                createDraft.reset();
              }}

              testID="schedule-draft.reorder-cancel"
            >
              <Text>취소</Text>
            </Button>
            <Button
              size="lg"
              className="flex-1"
              disabled={isSameLayoutOrder(reorderLayout, layout)}
              loading={recalculating}
              loadingLabel="계산하는 중"

              onPress={() => {
                recalculate({ layout: reorderLayout, doneMessage: null });
              }}

              testID="schedule-draft.reorder-recalculate"
            >
              <Text>다시 계산</Text>
            </Button>
          </View>
        </>
      );
    }

    return (
      <>
        {recalculation === null ? null : <RecalculationNotice progress={recalculation} />}
        {recalculateError}
        {applyDraft.isError ? <ErrorLine message={getScheduleErrorMessage(applyDraft.error, SCHEDULE_ERROR_FALLBACK.save)} testID="schedule-draft.apply-error" /> : null}
        {source.kind === "saved" && !recalculating && !createDraft.isError ? (
          <Text className="text-center text-caption text-muted-foreground">일정을 고치거나 다시 계산하면 저장할 수 있어요</Text>
        ) : null}
        <View className="w-full flex-row gap-2">
          <Button
            variant="outline"
            size="lg"
            disabled={applyDraft.isPending || selectedDay === undefined}
            loading={recalculating}
            loadingLabel="계산하는 중"

            onPress={() => {
              recalculate({ layout, doneMessage: null });
            }}

            testID="schedule-draft.recalculate"
          >
            <Text>다시 계산</Text>
          </Button>
          <Button
            size="lg"
            className="flex-1"
            disabled={source.kind !== "draft" || recalculating || selectedDay === undefined}
            loading={applyDraft.isPending}
            loadingLabel="저장하는 중"
            onPress={save}
            testID="schedule-draft.apply"
          >
            <Text>이 일정으로 저장</Text>
          </Button>
        </View>
      </>
    );
  };

  return (
    <Screen>
      <ScreenHeader title={source.kind === "saved" ? "일정 조정" : "일정 초안"} onBack={goBack} testID="schedule-draft.header" />
      <ScrollView className="flex-1" contentContainerClassName="gap-4 pb-6 pt-4" testID="schedule-draft.review">
        <EditorIntro source={source} schedule={schedule} />
        <TransportChangedNotice room={room} schedule={schedule} />
        {source.kind === "draft" && baseVersion !== null ? <ScheduleChangesNotice roomId={roomId} schedule={schedule} /> : null}
        {selectedDay === undefined ? (
          <Text className="px-5 text-body-sm text-foreground-secondary">일정에 넣은 날이 없어요</Text>
        ) : (
          <View>
            <DayChips
              items={chipItems}
              selectedDate={selectedDay.date}
              onSelect={setSelectedDate}

              onOpenPicker={() => {
                setPickerOpen(true);
              }}

              testID="schedule-draft.days"
            />
            {reorderDay === undefined ? (
              <DraftDay day={selectedDay} dayNumber={selectedIndex + 1} schedule={schedule} days={moveDays} locked={editingLocked} actions={dayActions} />
            ) : (
              <ReorderDay
                day={reorderDay}
                dayNumber={selectedIndex + 1}
                unplaced={selectedDay.unplaced}
                locked={recalculating}

                onMove={(stopId, direction) => {
                  setReorderLayout((current) => {
                    return current === null ? current : moveStopWithinDay(current, stopId, direction);
                  });
                }}
              />
            )}
          </View>
        )}
      </ScrollView>
      <BottomCta className="border-t border-border">{renderActions()}</BottomCta>
      {selectedDay === undefined ? null : (
        <DayPickerSheet
          open={pickerOpen}
          onOpenChange={setPickerOpen}
          items={chipItems}
          selectedDate={selectedDay.date}
          onSelect={setSelectedDate}
          testID="schedule-draft.day-picker"
        />
      )}
      <MoveStopSheet
        target={moveTarget}
        days={moveDays}
        onMove={moveStop}
        onRemove={removeFromSchedule}

        onClose={() => {
          setMoveTarget(null);
        }}
      />
      <StayTimeSheet
        target={stayTarget}
        onApply={changeStay}

        onClose={() => {
          setStayTarget(null);
        }}
      />
    </Screen>
  );
}

// #region 안내

function toDayChipItems(days: readonly ScheduleDay[]): DayChipItem[] {
  return days.map((day, index) => {
    return { date: day.date, label: dayLabelOf(index), caption: `${day.stops.length}곳`, hasWarning: hasUnplacedStops(day) };
  });
}

/** `자차로 이동해요 · 하루 일정 보통`. 모르는 값은 뺀다. */
function getDraftSummary(schedule: Pick<ScheduleBody, "transportMode" | "pace">): string | null {
  const parts: string[] = [];

  if (schedule.transportMode !== "UNKNOWN") {
    parts.push(`${withDirectionParticle(TRANSPORT_MODE_LABEL[schedule.transportMode])} 이동해요`);
  }

  if (schedule.pace !== "UNKNOWN") {
    parts.push(`하루 일정 ${SCHEDULE_PACE_LABEL[schedule.pace]}`);
  }

  return parts.length === 0 ? null : parts.join(" · ");
}

function EditorIntro({ source, schedule }: { source: ScheduleSource; schedule: ScheduleBody }) {
  const summary = getDraftSummary(schedule);
  const saved = source.kind === "saved";

  return (
    <View className="gap-2 px-5">
      <Text accessibilityRole="header" className="text-h2 text-foreground">{saved ? "지금 여행방 일정이에요" : "일정 초안이에요"}</Text>
      <Text className="text-body-sm text-foreground-secondary">
        {saved
          ? "장소를 다른 날로 옮기거나 머무는 시간·순서를 바꾸면 다시 계산해요. 저장하기 전에는 여행방 일정이 바뀌지 않아요."
          : "저장하면 여행방 일정이 돼요. 저장하기 전에는 지금 일정이 바뀌지 않아요."}
      </Text>
      {summary === null ? null : <Text className="text-caption text-muted-foreground">{summary}</Text>}
    </View>
  );
}

function Notice({ children, testID }: { children: ReactNode; testID: string }) {
  return (
    <View className="mx-5 flex-row gap-2 rounded-md bg-muted p-3" testID={testID}>
      <Icon as={Info} size={16} className="mt-0.5 text-icon" />
      <View className="flex-1 gap-1">{children}</View>
    </View>
  );
}

/** 일정을 만든 뒤 여행방 이동 수단이 바뀌면 이동 시간이 맞지 않는다(ScheduleTimeline 체크리스트). */
function TransportChangedNotice({ room, schedule }: { room: RoomDetail | undefined; schedule: ScheduleBody }) {
  if (room === undefined || room.transportMode === "UNKNOWN" || schedule.transportMode === "UNKNOWN" || room.transportMode === schedule.transportMode) {
    return null;
  }

  return (
    <Notice testID="schedule-draft.transport-changed">
      <Text className="text-caption text-foreground-secondary">
        {`여행방 이동 수단이 ${withDirectionParticle(TRANSPORT_MODE_LABEL[room.transportMode])} 바뀌었어요. 다시 계산하면 이동 시간이 맞춰져요.`}
      </Text>
    </Notice>
  );
}

function dayLabelsOf(schedule: ScheduleBody, dates: readonly LocalDate[]): string {
  return schedule.days.flatMap((day, index) => {
    return dates.includes(day.date) ? [dayLabelOf(index)] : [];
  }).join(", ");
}

/** 이미 일정이 있는 여행방이면 저장했을 때 달라지는 점을 알린다. 지금 일정을 못 받아도 검토는 막지 않는다. */
function ScheduleChangesNotice({ roomId, schedule }: { roomId: number; schedule: ScheduleBody }) {
  const currentQuery = useRoomSchedule(roomId);
  const current = currentQuery.data;
  const changes = current ? getScheduleChanges(current, schedule) : null;

  return (
    <Notice testID="schedule-draft.changes">
      <Text className="text-label text-foreground">저장하면 지금 일정이 이 초안으로 바뀌어요</Text>
      {changes === null ? null : (
        <>
          <Text className="text-caption text-foreground-secondary">
            {changes.changedDates.length === 0 ? "지금 일정과 장소·순서가 같아요" : `장소나 순서가 바뀌는 날: ${dayLabelsOf(schedule, changes.changedDates)}`}
          </Text>
          {changes.droppedStops.length === 0 ? null : (
            <Text className="text-caption text-foreground-secondary">
              {`일정에서 빠지는 장소: ${changes.droppedStops.map((stop) => {
                return stop.name;
              }).join(", ")}`}
            </Text>
          )}
        </>
      )}
    </Notice>
  );
}

// #endregion

// #region 하루 일정과 조정

/** `10:00–18:00 · 5곳 · 머무는 시간 4시간 30분 · 이동 1시간 10분`. 이동 시간을 모르면 "정보 없음". */
function getDaySummary(day: ScheduleDay): string {
  const travel = day.totalTravelMinutes === null ? "이동 시간 정보 없음" : `이동 ${formatDuration(day.totalTravelMinutes)}`;
  return `${day.startTime}–${day.endTime} · ${day.stops.length}곳 · 머무는 시간 ${formatDuration(day.totalStayMinutes)} · ${travel}`;
}

type DraftDayProps = {
  day: ScheduleDay;
  dayNumber: number;
  schedule: ScheduleBody;
  days: readonly MoveDayOption[];
  locked: boolean;
  actions: DayActions;
};

function DraftDay({ day, dayNumber, schedule, days, locked, actions }: DraftDayProps) {
  return (
    <View className="gap-3" testID={`schedule-draft.day-${day.date}`}>
      <View className="gap-1 px-5">
        <Text accessibilityRole="header" className="text-label text-foreground">{`${dayNumber}일차 ${formatShortMonthDay(day.date)}`}</Text>
        <Text className="text-caption text-foreground-secondary tabular-nums">{getDaySummary(day)}</Text>
      </View>
      {hasUnplacedStops(day) ? <UnplacedStops day={day} days={days} locked={locked} actions={actions} /> : null}
      {day.stops.length === 0 ? (
        <Text className="px-5 text-caption text-muted-foreground">이날은 넣은 장소가 없어요</Text>
      ) : (
        <>
          <ActionLegend />
          <ScheduleDayTimeline
            day={day}
            transportMode={schedule.transportMode}

            renderStopActions={(stop) => {
              return <StopActions stop={stop} day={day} locked={locked} actions={actions} />;
            }}

            testID="schedule-draft.timeline"
          />
        </>
      )}
    </View>
  );
}

const ACTION_LEGEND: readonly { icon: LucideIcon; label: string }[] = [
  { icon: ArrowLeftRight, label: "다른 날로 옮기기" },
  { icon: Hourglass, label: "머무는 시간" },
  { icon: GripVertical, label: "순서 바꾸기" },
];

/** 장소 카드 버튼 설명(W14). 버튼마다 접근성 이름이 있어 화면 낭독에서는 뺀다. */
function ActionLegend() {
  return (
    <View accessible={false} importantForAccessibility="no-hide-descendants" className="flex-row flex-wrap gap-x-4 gap-y-1 px-5">
      {ACTION_LEGEND.map((item) => {
        return (
          <View key={item.label} className="flex-row items-center gap-1">
            <Icon as={item.icon} size={14} className="text-muted-foreground" />
            <Text className="text-caption text-muted-foreground">{item.label}</Text>
          </View>
        );
      })}
    </View>
  );
}

type IconActionProps = { icon: LucideIcon; label: string; disabled: boolean; onPress: () => void; testID: string };

/** 장소 카드의 아이콘 버튼. 보이는 크기는 36×44이고 좌우 `hitSlop`으로 44×44를 맞춘다. */
function IconAction({ icon, label, disabled, onPress, testID }: IconActionProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      disabled={disabled}
      hitSlop={{ left: 4, right: 4 }}
      className="h-touch w-9 items-center justify-center rounded-sm active:bg-muted"
      onPress={onPress}
      testID={testID}
    >
      <Icon as={icon} size={18} className={disabled ? "text-disabled-foreground" : "text-icon"} />
    </Pressable>
  );
}

function StopActions({ stop, day, locked, actions }: { stop: ScheduleStop; day: ScheduleDay; locked: boolean; actions: DayActions }) {
  return (
    <View className="flex-row items-center gap-2">
      <IconAction
        icon={ArrowLeftRight}
        label={`${stop.name} 다른 날로 옮기기`}
        disabled={locked}

        onPress={() => {
          actions.openMove(stop, day);
        }}

        testID={`schedule-draft.move-${stop.id}`}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${stop.name} 머무는 시간 ${formatDuration(stop.stayMinutes)}, 바꾸기`}
        accessibilityState={{ disabled: locked }}
        disabled={locked}
        hitSlop={{ top: 6, bottom: 6 }}
        className="h-8 flex-row items-center gap-1 rounded-sm bg-muted px-2 active:bg-accent"

        onPress={() => {
          actions.openStay(stop);
        }}

        testID={`schedule-draft.stay-${stop.id}`}
      >
        <Icon as={Hourglass} size={14} className={locked ? "text-disabled-foreground" : "text-icon"} />
        <Text className="text-label-sm text-foreground tabular-nums">{`${stop.stayMinutes}분`}</Text>
      </Pressable>
      <IconAction icon={GripVertical} label={`${stop.name} 순서 바꾸기`} disabled={locked} onPress={actions.startReorder} testID={`schedule-draft.reorder-${stop.id}`} />
    </View>
  );
}

/** 시간 안에 다 넣지 못한 장소와 서버가 정한 이유(명세 §7). 서버가 권한 날이 있으면 바로 옮기고, 없으면 날을 고르게 한다. */
function UnplacedStops({ day, days, locked, actions }: { day: ScheduleDay; days: readonly MoveDayOption[]; locked: boolean; actions: DayActions }) {
  return (
    <View className="mx-5 gap-3 rounded-md bg-highlight-muted p-3" testID="schedule-draft.unplaced">
      <View className="flex-row items-center gap-2">
        <Icon as={TriangleAlert} size={16} className="text-highlight" />
        <Text accessibilityRole="alert" className="flex-1 text-label text-highlight">{`시간 안에 넣지 못한 장소 ${day.unplaced.length}곳`}</Text>
      </View>
      {day.unplaced.map((stop) => {
        const suggested = days.find((option) => {
          return option.date === stop.suggestedDate && option.date !== day.date;
        });

        const label = suggested === undefined ? "다른 날로 옮기기" : `${withDirectionParticle(suggested.label)} 옮기기`;

        return (
          <View key={stop.id} className="gap-0.5 pl-6">
            <Text className="text-label text-foreground">{stop.name}</Text>
            <Text className="text-caption text-foreground-secondary">{stop.reason}</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`${stop.name}, ${label}`}
              accessibilityState={{ disabled: locked }}
              disabled={locked}
              className="h-touch flex-row items-center gap-1 self-start"

              onPress={() => {
                if (suggested === undefined) {
                  actions.openMoveUnplaced(stop, day);
                  return;
                }

                actions.moveUnplaced(stop, suggested.date);
              }}

              testID={`schedule-draft.unplaced-move-${stop.id}`}
            >
              <Text className={cn("text-label", locked ? "text-disabled-foreground" : "text-primary")}>{label}</Text>
              <Icon as={ChevronRight} size={16} className={locked ? "text-disabled-foreground" : "text-primary"} />
            </Pressable>
          </View>
        );
      })}
    </View>
  );
}

type ReorderDayProps = {
  day: ScheduleLayoutDay;
  dayNumber: number;
  unplaced: readonly UnplacedStop[];
  locked: boolean;
  onMove: (stopId: number, direction: -1 | 1) => void;
};

/**
 * 순서 바꾸기(W14 ⠿). 드래그 대신 위·아래 버튼으로 옮긴다. 시각은 다시 계산해야 맞으므로 이 동안에는 보여 주지 않는다.
 * 넣지 못한 장소도 뼈대 끝에 있어 함께 옮길 수 있다.
 */
function ReorderDay({ day, dayNumber, unplaced, locked, onMove }: ReorderDayProps) {
  const unplacedIds = new Set(unplaced.map((stop) => {
    return stop.id;
  }));

  return (
    <View className="gap-3 px-5" testID="schedule-draft.reorder">
      <View className="gap-1">
        <Text accessibilityRole="header" className="text-label text-foreground">{`${dayNumber}일차 순서 바꾸기`}</Text>
        <Text className="text-caption text-foreground-secondary">위·아래 버튼으로 순서를 바꾼 뒤 다시 계산하면 시각과 이동 시간이 맞춰져요.</Text>
      </View>
      {day.stops.length === 0 ? <Text className="text-caption text-muted-foreground">이날은 넣은 장소가 없어요</Text> : null}
      {day.stops.map((stop, index) => {
        const stayLabel = `머무는 시간 ${formatDuration(stop.stayMinutes)}`;

        return (
          <View key={stop.id} className="flex-row items-center gap-3 rounded-md border border-border bg-card py-1 pl-3 pr-1" testID={`schedule-draft.reorder-row-${stop.id}`}>
            <View className="h-6 w-6 items-center justify-center rounded-full bg-primary">
              <Text className="text-micro text-primary-foreground">{index + 1}</Text>
            </View>
            <View className="flex-1 gap-0.5 py-2">
              <Text className="text-label text-foreground">{stop.name}</Text>
              <Text className="text-caption text-foreground-secondary">{unplacedIds.has(stop.id) ? `${stayLabel} · 시간 안에 넣지 못한 장소` : stayLabel}</Text>
            </View>
            <IconAction
              icon={ChevronUp}
              label={`${stop.name} 앞으로`}
              disabled={locked || index === 0}

              onPress={() => {
                onMove(stop.id, -1);
              }}

              testID={`schedule-draft.reorder-up-${stop.id}`}
            />
            <IconAction
              icon={ChevronDown}
              label={`${stop.name} 뒤로`}
              disabled={locked || index === day.stops.length - 1}

              onPress={() => {
                onMove(stop.id, 1);
              }}

              testID={`schedule-draft.reorder-down-${stop.id}`}
            />
          </View>
        );
      })}
    </View>
  );
}

// #endregion

// #region 하단 상태

function ErrorLine({ message, onRetry, testID }: { message: string; onRetry?: () => void; testID: string }) {
  return (
    <View className="w-full flex-row flex-wrap items-center justify-center gap-x-2" testID={testID}>
      <Text accessibilityRole="alert" className="text-center text-caption text-destructive">{message}</Text>
      {onRetry === undefined ? null : (
        <Pressable accessibilityRole="button" hitSlop={{ top: 13, bottom: 13, left: 8, right: 8 }} onPress={onRetry} testID={`${testID}.retry`}>
          <Text className="text-caption text-primary">다시 시도</Text>
        </Pressable>
      )}
    </View>
  );
}

/** 다시 계산이 오래 걸리거나 진행 확인이 늦을 때만 보인다. 진행 확인 실패는 계산 실패가 아니다(규칙 20). */
function RecalculationNotice({ progress }: { progress: RecalculationProgress }) {
  const elapsedSeconds = useElapsedSeconds(true);
  const waitedTooLong = elapsedSeconds * MS_PER_SECOND >= SCHEDULE_DRAFT_MAX_WAIT_MS;

  if (!waitedTooLong && !progress.connectionTrouble) {
    return null;
  }

  return (
    <View accessibilityLiveRegion="polite" className="w-full items-center gap-1" testID="schedule-draft.recalculating">
      <Text className="text-center text-caption text-foreground-secondary">
        {waitedTooLong ? "아직 계산하고 있어요. 화면을 나가도 계산은 이어져요." : "연결이 불안정해 확인이 늦어지고 있어요. 다시 연결되면 이어서 확인해요."}
      </Text>
      {waitedTooLong ? (
        <Pressable accessibilityRole="button" hitSlop={{ top: 13, bottom: 13, left: 8, right: 8 }} disabled={progress.checking} onPress={progress.onCheckAgain} testID="schedule-draft.recalculating.check">
          <Text className="text-caption text-primary">{progress.checking ? "확인하는 중" : "다시 확인"}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

/** 그사이 일행이 일정을 바꿔 저장이 거절됐다(409 C008). 덮어쓰지 않고 바뀐 일정을 보거나 다시 만들게 한다. */
function ConflictActions({ roomId }: { roomId: number }) {
  return (
    <>
      <Text accessibilityRole="alert" className="text-center text-caption text-destructive" testID="schedule-draft.conflict">
        그사이 일행이 일정을 바꿔서 이 초안을 저장하지 못했어요. 바뀐 일정을 보고 다시 만들어 주세요.
      </Text>
      <View className="w-full flex-row gap-2">
        <Button
          variant="outline"
          size="lg"
          className="flex-1"

          onPress={() => {
            goToRoomSchedule(roomId);
          }}

          testID="schedule-draft.view-latest"
        >
          <Text>바뀐 일정 보기</Text>
        </Button>
        <Button
          size="lg"
          className="flex-1"

          onPress={() => {
            goToScheduleConditions(roomId);
          }}

          testID="schedule-draft.recreate"
        >
          <Text>다시 만들기</Text>
        </Button>
      </View>
    </>
  );
}

// #endregion
