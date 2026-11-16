import { router } from "expo-router";
import { ArrowLeftRight, Bus, Car, ChevronRight, Clock, GripVertical, Hourglass, Info, MapPin, Trash2, TriangleAlert, type LucideIcon } from "lucide-react-native";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Modal, Pressable, ScrollView, View } from "react-native";

import { BottomCta } from "@/components/ui/bottom-cta";
import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { MapViewToggle } from "@/features/room/components/map-view-toggle";
import { PlaceRouteMap } from "@/features/room/components/place-route-map";
import { TRANSPORT_MODE_LABEL, type RoomDetail, type TransportMode } from "@/features/room/model";
import { SCHEDULE_DRAFT_MAX_WAIT_MS, useAdjustScheduleDraft, useRoomSchedule, useSaveSchedule } from "@/features/schedule/api/queries";
import { DayChips, type DayChipItem } from "@/features/schedule/components/day-chips";
import { DayPickerSheet } from "@/features/schedule/components/day-picker-sheet";
import { MoveStopSheet, type MoveDayOption, type MoveStopTarget } from "@/features/schedule/components/move-stop-sheet";
import { ScheduleDayTimeline, type TimelineRemove, type TimelineReorder } from "@/features/schedule/components/schedule-day-timeline";
import { StayTimeSheet, type StayTarget } from "@/features/schedule/components/stay-time-sheet";
import { TimePickerSheet } from "@/features/schedule/components/time-picker-sheet";
import { getScheduleErrorMessage, isScheduleConflict, SCHEDULE_ERROR_FALLBACK } from "@/features/schedule/errors";
import {
  estimateEditedDay,
  formatDuration,
  getEditedDates,
  getItemTimeLabel,
  getPlacedDates,
  getPlaceName,
  getScheduleChanges,
  getScheduleWarningMessage,
  getTotalStayMinutes,
  getTotalTravelMinutes,
  hasPendingTravel,
  moveItemToDay,
  moveItemToIndex,
  setDayEndTime,
  setItemStay,
  toAdjustDraftRequest,
  toDraftLayout,
  toSaveScheduleRequest,
  toScheduleMapStops,
  unassignItem,
  type DraftLayout,
  type DraftLayoutDay,
  type DraftLayoutItem,
  type LocalTime,
  type ReadyScheduleDraft,
  type ScheduleDay,
  type ScheduleItem,
  type SchedulePlaceLookup,
  type ScheduleWarning,
} from "@/features/schedule/model";
import { goToRoomSchedule } from "@/features/schedule/navigation";
import { useElapsedSeconds } from "@/hooks/use-elapsed-seconds";
import { formatShortMonthDay, type LocalDate } from "@/lib/date";
import { withDirectionParticle, withObjectParticle } from "@/lib/korean";
import { cn } from "@/lib/utils";

const MS_PER_SECOND = 1_000;

/** 되돌리기 알림을 띄우는 시간. */
const UNDO_TOAST_MS = 4_000;

/**
 * 고친 뒤 이동 시간을 자동으로 다시 계산(API-084)하기까지 기다리는 시간. 그사이 또 고치면 마지막에 한 번만 계산한다.
 * 되돌리기가 있는 수정은 알림이 사라진 뒤에 계산한다. 계산하는 동안 되돌리면 계산이 끝나면서 되돌린 내용이 사라지기 때문이다.
 */
const AUTO_RECALCULATE_MS = { edit: 1_200, undoable: UNDO_TOAST_MS + 300 } as const;

/** 다시 계산하는 동안의 진행 확인 상태. */
export type RecalculationProgress = { checking: boolean; connectionTrouble: boolean; onCheckAgain: () => void };

type ScheduleEditorProps = {
  roomId: number;
  draftKey: string;
  room: RoomDetail | undefined;
  /** 계산을 마친 내용이 있는 초안. 다시 계산하는 중이면 그동안 고치기와 저장을 막는다. */
  draft: ReadyScheduleDraft;
  /** 뺀 후보까지 포함한 여행방 후보. 장소 이름을 찾는다. */
  places: SchedulePlaceLookup;
  /** 저장된 일정에서 시작한 초안인지("일정 조정하기"). */
  adjusting: boolean;
  progress: RecalculationProgress;
  /** 이 초안을 버리고 다시 만든다(그사이 여행방이 바뀌어 저장할 수 없을 때). */
  onRecreate: () => void;
};

/** 화면에서 고친 배치. 받은 초안이 바뀌면(`draftVersion`) 버리고 새 초안을 따른다. */
type LocalEdits = { draftVersion: number; layout: DraftLayout };

/** 장소 카드에서 하는 일. */
type DayActions = {
  openMove: (item: ScheduleItem, name: string) => void;
  openStay: (item: ScheduleItem, name: string) => void;
};

/** "종료 시각 늘리기" 대상 날. `place`가 있으면 늘린 날에 그 장소도 넣는다. */
type EndTimeTarget = { date: LocalDate; dayLabel: string; endTime: LocalTime; place: { itemId: number; name: string } | null };

/**
 * 다시 계산(084)에 보낸 배치. 결과가 오면 보낸 날에 있었는데 미배치로 돌아온 항목을 찾는다.
 * 서버는 종료 시각을 넘기는 날에서 고정하지 않은 마지막 항목을 미배치로 옮기기 때문이다(백엔드 ScheduleDraftAdjustService.trimOverflow).
 * `carried`는 앞 계산에서 이미 빠져 이번에도 미배치로 보낸 항목과 넣으려던 날이다.
 */
type SentPlacement = { fromVersion: number; placedDates: ReadonlyMap<number, LocalDate>; carried: ReadonlyMap<number, LocalDate> };

/** 시간이 모자라 서버가 뺀 항목과 넣으려던 날. 결과가 오기 전에는 앞 계산에서 빠진 것만 있다. */
function getTrimmedDates(sent: SentPlacement | null, draft: ReadyScheduleDraft, recalculating: boolean): Map<number, LocalDate> {
  const trimmed = new Map<number, LocalDate>();

  if (sent === null) {
    return trimmed;
  }

  const resultArrived = !recalculating && draft.draftVersion > sent.fromVersion;

  for (const item of draft.schedule.unassigned) {
    const date = (resultArrived ? sent.placedDates.get(item.itemId) : undefined) ?? sent.carried.get(item.itemId);

    if (date !== undefined) {
      trimmed.set(item.itemId, date);
    }
  }

  return trimmed;
}

function goBack() {
  router.back();
}

function dayLabelOf(index: number): string {
  return `${index + 1}일차`;
}

/** 이동 시간을 아직 모르는 구간이 있는 날의 안내. */
function getPendingTravelMessage(recalculating: boolean, scheduled: boolean): string {
  if (recalculating) {
    return "바뀐 순서로 이동 시간을 계산하고 있어요.";
  }

  if (scheduled) {
    return "잠시 뒤 바뀐 순서로 이동 시간을 계산해요.";
  }

  return "바뀐 구간의 이동 시간을 아직 계산하지 못했어요. 다시 계산을 눌러 주세요.";
}

/** 동작 하나를 정한 시간 뒤에 부른다. 다시 예약하면 앞 예약을 지우되 더 늦은 시각을 따른다. 화면을 나가면 지운다. */
function useScheduledCall() {
  const pendingRef = useRef<{ timer: ReturnType<typeof setTimeout>; dueAt: number } | null>(null);
  const [scheduled, setScheduled] = useState(false);

  useEffect(() => {
    return () => {
      if (pendingRef.current !== null) {
        clearTimeout(pendingRef.current.timer);
      }
    };
  }, []);

  const clear = () => {
    if (pendingRef.current !== null) {
      clearTimeout(pendingRef.current.timer);
      pendingRef.current = null;
    }
  };

  return {
    scheduled,

    schedule: (call: () => void, delayMs: number) => {
      const dueAt = Math.max(Date.now() + delayMs, pendingRef.current?.dueAt ?? 0);
      clear();

      const timer = setTimeout(() => {
        pendingRef.current = null;
        setScheduled(false);
        call();
      }, dueAt - Date.now());

      pendingRef.current = { timer, dueAt };
      setScheduled(true);
    },

    cancel: () => {
      clear();
      setScheduled(false);
    },
  };
}

/**
 * 일정 초안 검토·조정(Figma W14·W14-M·W14-T, TRP-08·09).
 * 다른 날로 옮기기·빼기·머무는 시간·순서 바꾸기(카드 손잡이를 길게 눌러 끌기)는 화면의 배치만 고친다.
 * 고친 날의 시각은 받은 초안의 이동 시간으로 화면에서 다시 맞추고, 받은 초안에 없는 구간이 생기면 잠시 뒤 "다시 계산"(API-084)을
 * 자동으로 부른다. 저장(API-039)은 다시 계산하지 않아도 할 수 있다. 저장하기 전에는 여행방 일정을 바꾸지 않는다.
 */
export function ScheduleEditor({ roomId, draftKey, room, draft, places, adjusting, progress, onRecreate }: ScheduleEditorProps) {
  const [selectedDate, setSelectedDate] = useState(draft.conditions.at(0)?.date);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [edits, setEdits] = useState<LocalEdits | null>(null);
  const [dragging, setDragging] = useState(false);
  const [moveTarget, setMoveTarget] = useState<MoveStopTarget | null>(null);
  const [stayTarget, setStayTarget] = useState<StayTarget | null>(null);
  const [endTimeTarget, setEndTimeTarget] = useState<EndTimeTarget | null>(null);
  const [sentPlacement, setSentPlacement] = useState<SentPlacement | null>(null);
  const [recalculatedFrom, setRecalculatedFrom] = useState<number | null>(null);
  const [mapOpen, setMapOpen] = useState(false);
  const adjustDraft = useAdjustScheduleDraft();
  const saveDraft = useSaveSchedule();
  const autoRecalculation = useScheduledCall();
  const toast = useToast();

  const serverLayout = toDraftLayout(draft);
  const layout = edits !== null && edits.draftVersion === draft.draftVersion ? edits.layout : serverLayout;
  const editedDates = getEditedDates(serverLayout, layout);
  const recalculating = draft.recalculating || adjustDraft.isPending;
  const editingLocked = recalculating || saveDraft.isPending;

  // 다시 계산이 끝났는데 초안 버전이 그대로면 서버가 계산에 실패해 마지막 결과로 돌아간 것이다.
  const recalculationFailed = recalculatedFrom === draft.draftVersion && !recalculating && !adjustDraft.isError;

  const selectedIndex = Math.max(
    layout.days.findIndex((day) => {
      return day.date === selectedDate;
    }),
    0,
  );

  const selectedLayoutDay = layout.days.at(selectedIndex);
  const selectedEdited = selectedLayoutDay !== undefined && editedDates.includes(selectedLayoutDay.date);

  const calculatedDay = selectedEdited ? undefined : draft.schedule.days.find((day) => {
    return day.date === selectedLayoutDay?.date;
  });

  const estimate = selectedLayoutDay === undefined || calculatedDay !== undefined ? null : estimateEditedDay(selectedLayoutDay, draft);
  const selectedDay = calculatedDay ?? estimate?.day;
  const pendingTravelMessage = estimate?.pendingTravel ? getPendingTravelMessage(recalculating, autoRecalculation.scheduled) : null;

  const warningsOf = (day: DraftLayoutDay): readonly ScheduleWarning[] => {
    if (editedDates.includes(day.date)) {
      return estimateEditedDay(day, draft).day.warnings;
    }

    return draft.schedule.days.find((item) => {
      return item.date === day.date;
    })?.warnings ?? [];
  };

  const chipItems: DayChipItem[] = layout.days.map((day, index) => {
    return { date: day.date, label: dayLabelOf(index), caption: `${day.items.length}곳`, hasWarning: warningsOf(day).length > 0 };
  });

  const moveDays: MoveDayOption[] = layout.days.map((day, index) => {
    return { date: day.date, label: dayLabelOf(index), stopCount: day.items.length };
  });

  const labelOfDate = (date: LocalDate) => {
    return moveDays.find((day) => {
      return day.date === date;
    })?.label ?? "";
  };

  const trimmedDates = getTrimmedDates(sentPlacement, draft, recalculating);

  const openEndTime = (date: LocalDate, place: EndTimeTarget["place"]) => {
    const day = layout.days.find((candidate) => {
      return candidate.date === date;
    });

    if (day !== undefined) {
      setEndTimeTarget({ date, dayLabel: labelOfDate(date), endTime: day.endTime, place });
    }
  };

  const recalculateLayout = (target: DraftLayout) => {
    const unassignedIds = new Set(target.unassigned.map((item) => {
      return item.itemId;
    }));

    saveDraft.reset();
    setRecalculatedFrom(null);

    setSentPlacement({
      fromVersion: draft.draftVersion,
      placedDates: getPlacedDates(target),

      carried: new Map([...trimmedDates].filter(([itemId]) => {
        return unassignedIds.has(itemId);
      })),
    });

    adjustDraft.mutate(
      { roomId, draftKey, request: toAdjustDraftRequest(draft, serverLayout, target) },
      {
        onSuccess: () => {
          setRecalculatedFrom(draft.draftVersion);
        },
      },
    );
  };

  /** 받은 초안에 없는 구간이 남아 있으면 잠시 뒤 다시 계산을 자동으로 부른다. */
  const scheduleRecalculation = (target: DraftLayout, delayMs: number) => {
    if (!hasPendingTravel(draft, serverLayout, target)) {
      autoRecalculation.cancel();
      return;
    }

    autoRecalculation.schedule(() => {
      recalculateLayout(target);
    }, delayMs);
  };

  const applyEdit = (next: DraftLayout, recalculateAfterMs: number) => {
    adjustDraft.reset();
    saveDraft.reset();
    setRecalculatedFrom(null);
    setEdits({ draftVersion: draft.draftVersion, layout: next });
    scheduleRecalculation(next, recalculateAfterMs);
  };

  const applyUndoableEdit = (next: DraftLayout, message: string) => {
    const previous = layout;
    applyEdit(next, AUTO_RECALCULATE_MS.undoable);

    toast.show({
      message,
      durationMs: UNDO_TOAST_MS,

      action: {
        label: "되돌리기",

        onPress: () => {
          applyEdit(previous, AUTO_RECALCULATE_MS.edit);
        },
      },
    });
  };

  const moveItem = (target: MoveStopTarget, toDate: LocalDate) => {
    setMoveTarget(null);
    applyUndoableEdit(moveItemToDay(layout, target.itemId, toDate), `${withDirectionParticle(labelOfDate(toDate))} 옮겼어요`);
  };

  /** 일정에서 빼 후보 장소로 돌려보낸다. 여행방 후보에서 지우지는 않는다. */
  const removeStop = (itemId: number, name: string) => {
    applyUndoableEdit(unassignItem(layout, itemId), `${withObjectParticle(name)} 후보 장소로 돌려보냈어요`);
  };

  const removeFromSchedule = (target: MoveStopTarget) => {
    setMoveTarget(null);
    removeStop(target.itemId, target.name);
  };

  const remove: TimelineRemove = { disabled: editingLocked, onRemove: removeStop };

  const changeStay = (target: StayTarget, stayMinutes: number) => {
    setStayTarget(null);
    applyEdit(setItemStay(layout, target.itemId, stayMinutes), AUTO_RECALCULATE_MS.edit);
  };

  /** 종료 시각을 늘린다. 시간이 모자라 빠진 장소면 그날 끝에 다시 넣고, 잠시 뒤 바뀐 시각으로 다시 계산한다. */
  const extendEndTime = (target: EndTimeTarget, endTime: LocalTime) => {
    setEndTimeTarget(null);
    const extended = setDayEndTime(layout, target.date, endTime);

    if (target.place === null) {
      applyEdit(extended, AUTO_RECALCULATE_MS.edit);
      return;
    }

    applyUndoableEdit(
      moveItemToDay(extended, target.place.itemId, target.date),
      `${target.dayLabel} 종료 시각을 ${endTime}까지 늘리고 ${withObjectParticle(target.place.name)} 넣었어요`,
    );
  };

  // 끄는 동안에는 자동 계산을 미룬다. 계산이 시작되면 고치기가 잠겨 끌던 카드가 놓이기 때문이다.
  const reorder: TimelineReorder = {
    disabled: editingLocked,

    onDragStart: () => {
      setDragging(true);
      autoRecalculation.cancel();
    },

    onDragEnd: () => {
      setDragging(false);
      scheduleRecalculation(layout, AUTO_RECALCULATE_MS.edit);
    },

    onMove: (itemId, toIndex) => {
      applyEdit(moveItemToIndex(layout, itemId, toIndex), AUTO_RECALCULATE_MS.edit);
    },
  };

  const recalculate = () => {
    autoRecalculation.cancel();
    recalculateLayout(layout);
  };

  const save = () => {
    autoRecalculation.cancel();
    adjustDraft.reset();
    setRecalculatedFrom(null);

    saveDraft.mutate(
      { roomId, request: toSaveScheduleRequest(draftKey, draft, layout) },
      {
        onSuccess: () => {
          toast.show({ message: "일정을 저장했어요" });
          goToRoomSchedule(roomId);
        },
      },
    );
  };

  /** 장소의 시각. 고친 날은 화면에서 다시 맞춘 값이고, 모르면 `null`. */
  const timeTextOf = (item: ScheduleItem) => {
    return item.arrivalTime === null ? null : getItemTimeLabel(item);
  };

  const dayActions: DayActions = {
    openMove: (item, name) => {
      if (selectedLayoutDay === undefined) {
        return;
      }

      const position = [`지금 ${labelOfDate(selectedLayoutDay.date)}`, timeTextOf(item)].filter(Boolean).join(" ");
      setMoveTarget({ itemId: item.id, name, fromDate: selectedLayoutDay.date, detail: position });
    },

    openStay: (item, name) => {
      const timeText = timeTextOf(item);
      setStayTarget({ itemId: item.id, name, stayMinutes: item.stayMinutes, detail: timeText === null ? null : `지금 ${timeText}` });
    },
  };

  const openMoveUnassigned = (item: DraftLayoutItem) => {
    setMoveTarget({ itemId: item.itemId, name: getPlaceName(places, item.roomPlaceId), fromDate: null, detail: "지금 일정에 넣지 않은 장소" });
  };

  const renderActions = () => {
    if (isScheduleConflict(saveDraft.error) || isScheduleConflict(adjustDraft.error)) {
      return <ConflictActions roomId={roomId} onRecreate={onRecreate} />;
    }

    return (
      <>
        {draft.recalculating ? <RecalculationNotice progress={progress} /> : null}
        {recalculationFailed ? (
          <ErrorLine message="다시 계산하지 못했어요. 잠시 후 다시 시도해 주세요." onRetry={recalculate} testID="schedule-draft.recalculate-failed" />
        ) : null}
        {adjustDraft.isError ? (
          <ErrorLine message={getScheduleErrorMessage(adjustDraft.error, SCHEDULE_ERROR_FALLBACK.recalculate)} onRetry={recalculate} testID="schedule-draft.recalculate-error" />
        ) : null}
        {saveDraft.isError ? <ErrorLine message={getScheduleErrorMessage(saveDraft.error, SCHEDULE_ERROR_FALLBACK.save)} testID="schedule-draft.apply-error" /> : null}
        <View className="w-full flex-row gap-2">
          <Button
            variant="outline"
            size="lg"
            disabled={saveDraft.isPending || selectedDay === undefined}
            loading={recalculating}
            loadingLabel="계산하는 중"
            onPress={recalculate}
            testID="schedule-draft.recalculate"
          >
            <Text>다시 계산</Text>
          </Button>
          <Button
            size="lg"
            className="flex-1"
            disabled={recalculating || selectedDay === undefined}
            loading={saveDraft.isPending}
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

  const renderDay = () => {
    if (selectedDay === undefined) {
      return <Text className="px-5 text-body-sm text-foreground-secondary">일정에 넣은 날이 없어요</Text>;
    }

    return (
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
        <DraftDay
          day={selectedDay}
          dayNumber={selectedIndex + 1}
          pendingTravelMessage={pendingTravelMessage}
          calculating={recalculating}

          onExtendEndTime={() => {
            openEndTime(selectedDay.date, null);
          }}

          draft={draft}
          places={places}
          locked={editingLocked}
          actions={dayActions}
          reorder={reorder}
          remove={remove}
        />
      </View>
    );
  };

  // 지도는 고른 날의 지금 순서(저장 전 편집 포함)를 보여 준다.
  const mapStops = selectedDay === undefined ? [] : toScheduleMapStops(selectedDay.items, places);

  const closeMap = () => {
    setMapOpen(false);
  };

  return (
    <Screen>
      <ScreenHeader title={adjusting ? "일정 조정" : "일정 초안"} onBack={goBack} testID="schedule-draft.header" />
      <View className="flex-1">
        <ScrollView className="flex-1" contentContainerClassName="gap-4 pb-16 pt-4" scrollEnabled={!dragging} testID="schedule-draft.review">
          <EditorIntro adjusting={adjusting} draft={draft} />
          {room?.hasSchedule ? <ScheduleChangesNotice roomId={roomId} layout={layout} places={places} /> : null}
          {editedDates.length === 0 && draft.schedule.warnings.length > 0 ? <WarningList warnings={draft.schedule.warnings} testID="schedule-draft.warnings" /> : null}
          {layout.unassigned.length === 0 ? null : (
            <UnassignedItems
              items={layout.unassigned}
              places={places}
              locked={editingLocked}
              onMove={openMoveUnassigned}

              trimmedFrom={(item) => {
                const date = trimmedDates.get(item.itemId);

                const day = layout.days.find((candidate) => {
                  return candidate.date === date;
                });

                return day === undefined ? null : { date: day.date, dayLabel: labelOfDate(day.date), endTime: day.endTime };
              }}

              onExtend={(item, date) => {
                openEndTime(date, { itemId: item.itemId, name: getPlaceName(places, item.roomPlaceId) });
              }}
            />
          )}
          {renderDay()}
        </ScrollView>
        {mapStops.length === 0 ? null : (
          <View pointerEvents="box-none" className="absolute bottom-3 left-0 right-0 items-center">
            <MapViewToggle
              target="map"

              onPress={() => {
                setMapOpen(true);
              }}

              testID="schedule-draft.map-toggle"
            />
          </View>
        )}
      </View>
      <BottomCta className="border-t border-border">{renderActions()}</BottomCta>
      <Modal visible={mapOpen && mapStops.length > 0} animationType="slide" statusBarTranslucent onRequestClose={closeMap} testID="schedule-draft.map">
        <Screen>
          <ScreenHeader title={`${selectedIndex + 1}일차 지도`} onBack={closeMap} testID="schedule-draft.map-header" />
          <PlaceRouteMap
            places={mapStops}
            connected
            footer={<MapViewToggle target="list" onPress={closeMap} testID="schedule-draft.map-list-toggle" />}
            accessibilityLabel="고른 날의 일정을 방문 순서대로 이은 지도"
            testID="schedule-draft.map-route"
          />
        </Screen>
      </Modal>
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
        onMove={moveItem}
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
      <TimePickerSheet
        title={endTimeTarget === null ? null : `${endTimeTarget.dayLabel} 종료`}
        value={endTimeTarget?.endTime ?? ""}
        after={endTimeTarget?.endTime}

        onSelect={(time) => {
          if (endTimeTarget !== null) {
            extendEndTime(endTimeTarget, time);
          }
        }}

        onClose={() => {
          setEndTimeTarget(null);
        }}
      />
    </Screen>
  );
}

// #region 안내

function EditorIntro({ adjusting, draft }: { adjusting: boolean; draft: ReadyScheduleDraft }) {
  return (
    <View className="gap-2 px-5">
      <Text accessibilityRole="header" className="text-h2 text-foreground">{adjusting ? "지금 여행방 일정에서 시작했어요" : "일정 초안이에요"}</Text>
      <Text className="text-body-sm text-foreground-secondary">
        {adjusting
          ? "장소를 다른 날로 옮기거나 머무는 시간·순서를 바꾼 뒤 저장해 주세요. 저장하기 전에는 여행방 일정이 바뀌지 않아요."
          : "저장하면 여행방 일정이 돼요. 저장하기 전에는 지금 일정이 바뀌지 않아요."}
      </Text>
      {draft.transportMode === "UNKNOWN" ? null : (
        <Text className="text-caption text-muted-foreground">{`${withDirectionParticle(TRANSPORT_MODE_LABEL[draft.transportMode])} 이동해요`}</Text>
      )}
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

function dayLabelsOf(layout: DraftLayout, dates: readonly LocalDate[]): string {
  return layout.days.flatMap((day, index) => {
    return dates.includes(day.date) ? [dayLabelOf(index)] : [];
  }).join(", ");
}

/** 이미 일정이 있는 여행방이면 저장했을 때 달라지는 점을 알린다. 지금 일정을 못 받아도 검토는 막지 않는다. */
function ScheduleChangesNotice({ roomId, layout, places }: { roomId: number; layout: DraftLayout; places: SchedulePlaceLookup }) {
  const currentQuery = useRoomSchedule(roomId);
  const current = currentQuery.data;
  const changes = current ? getScheduleChanges(current, layout) : null;

  return (
    <Notice testID="schedule-draft.changes">
      <Text className="text-label text-foreground">저장하면 지금 일정이 이 초안으로 바뀌어요</Text>
      {changes === null ? null : (
        <>
          <Text className="text-caption text-foreground-secondary">
            {changes.changedDates.length === 0 ? "지금 일정과 장소·순서가 같아요" : `장소나 순서가 바뀌는 날: ${dayLabelsOf(layout, changes.changedDates)}`}
          </Text>
          {changes.droppedPlaceIds.length === 0 ? null : (
            <Text className="text-caption text-foreground-secondary">
              {`일정에서 빠지는 장소: ${changes.droppedPlaceIds.map((roomPlaceId) => {
                return getPlaceName(places, roomPlaceId);
              }).join(", ")}`}
            </Text>
          )}
        </>
      )}
    </Notice>
  );
}

/** 서버가 붙인 경고(이동 시간·도착 시각을 모름, 종료 시각을 넘김). */
function WarningList({ warnings, testID }: { warnings: readonly ScheduleWarning[]; testID: string }) {
  return (
    <View className="mx-5 gap-1 rounded-md bg-highlight-muted p-3" testID={testID}>
      {warnings.map((warning) => {
        return (
          <View key={warning} className="flex-row gap-2">
            <Icon as={TriangleAlert} size={16} className="mt-0.5 text-highlight" />
            <Text accessibilityRole="alert" className="flex-1 text-caption text-highlight">{getScheduleWarningMessage(warning)}</Text>
          </View>
        );
      })}
    </View>
  );
}

/** 시간이 모자라 빠진 장소를 넣으려던 날. */
type TrimmedFrom = { date: LocalDate; dayLabel: string; endTime: LocalTime };

type UnassignedItemsProps = {
  items: readonly DraftLayoutItem[];
  places: SchedulePlaceLookup;
  locked: boolean;
  onMove: (item: DraftLayoutItem) => void;
  /** 다시 계산에서 시간이 모자라 빠진 장소면 넣으려던 날. 아니면 `null`. */
  trimmedFrom: (item: DraftLayoutItem) => TrimmedFrom | null;
  /** 넣으려던 날의 종료 시각을 늘리고 다시 넣는다. */
  onExtend: (item: DraftLayoutItem, date: LocalDate) => void;
};

/**
 * 일정에 넣지 않은 장소. 서버가 시간 안에 넣지 못했거나 사용자가 뺀 항목이다.
 * 다시 계산에서 종료 시각을 넘겨 빠진 장소는 넣으려던 날과 종료 시각을 알려 주고, 그날 종료 시각을 늘려 다시 넣을 수 있게 한다.
 */
function UnassignedItems({ items, places, locked, onMove, trimmedFrom, onExtend }: UnassignedItemsProps) {
  return (
    <View className="mx-5 gap-3 rounded-md bg-highlight-muted p-3" testID="schedule-draft.unplaced">
      <View className="flex-row items-center gap-2">
        <Icon as={TriangleAlert} size={16} className="text-highlight" />
        <Text accessibilityRole="alert" className="flex-1 text-label text-highlight">{`일정에 넣지 않은 장소 ${items.length}곳`}</Text>
      </View>
      <Text className="pl-6 text-caption text-foreground-secondary">시간 안에 넣지 못했거나 일정에서 뺀 장소예요. 넣을 날을 고를 수 있어요.</Text>
      {items.map((item) => {
        const name = getPlaceName(places, item.roomPlaceId);
        const trimmed = trimmedFrom(item);

        return (
          <View key={item.itemId} className="gap-0.5 pl-6">
            <Text className="text-label text-foreground">{name}</Text>
            {trimmed === null ? null : (
              <Text className="text-caption text-foreground-secondary" testID={`schedule-draft.unplaced-reason-${item.itemId}`}>
                {`${trimmed.dayLabel} 종료 시각(${trimmed.endTime})까지 시간이 모자라 넣지 못했어요.`}
              </Text>
            )}
            <View className="flex-row flex-wrap gap-x-4">
              {trimmed === null ? null : (
                <UnplacedAction
                  label={`${trimmed.dayLabel} 종료 시각 늘리고 넣기`}
                  accessibilityLabel={`${name}, ${trimmed.dayLabel} 종료 시각 늘리고 넣기`}
                  locked={locked}

                  onPress={() => {
                    onExtend(item, trimmed.date);
                  }}

                  testID={`schedule-draft.unplaced-extend-${item.itemId}`}
                />
              )}
              <UnplacedAction
                label="넣을 날 고르기"
                accessibilityLabel={`${name}, 넣을 날 고르기`}
                locked={locked}

                onPress={() => {
                  onMove(item);
                }}

                testID={`schedule-draft.unplaced-move-${item.itemId}`}
              />
            </View>
          </View>
        );
      })}
    </View>
  );
}

type UnplacedActionProps = { label: string; accessibilityLabel: string; locked: boolean; onPress: () => void; testID: string };

function UnplacedAction({ label, accessibilityLabel, locked, onPress, testID }: UnplacedActionProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={{ disabled: locked }}
      disabled={locked}
      className="h-touch flex-row items-center gap-1 self-start"
      onPress={onPress}
      testID={testID}
    >
      <Text className={cn("text-label", locked ? "text-disabled-foreground" : "text-primary")}>{label}</Text>
      <Icon as={ChevronRight} size={16} className={locked ? "text-disabled-foreground" : "text-primary"} />
    </Pressable>
  );
}

// #endregion

// #region 하루 일정과 조정

/** 하루 요약 한 칸. `known`이 아니면(계산 전·정보 없음) 값을 흐리게 보여 준다. */
type DaySummaryCell = { icon: LucideIcon; label: string; value: string; known: boolean };

/** 그날 이동 시간 합계. 계산 전 구간이 있으면 합계를 내지 않고, 서버가 모르는 구간이 있으면 "정보 없음". */
function getTravelSummary(day: ScheduleDay, pendingTravel: boolean, calculating: boolean): Pick<DaySummaryCell, "value" | "known"> {
  if (pendingTravel) {
    return { value: calculating ? "계산하는 중" : "계산 전", known: false };
  }

  if (day.legs.length === 0) {
    return { value: "없음", known: true };
  }

  const travel = getTotalTravelMinutes(day.legs);
  return travel === null ? { value: "정보 없음", known: false } : { value: formatDuration(travel), known: true };
}

function getDaySummaryCells(day: ScheduleDay, transportMode: TransportMode, pendingTravel: boolean, calculating: boolean): DaySummaryCell[] {
  const hasTime = day.startTime !== null && day.endTime !== null;

  return [
    { icon: Clock, label: "시간", value: hasTime ? `${day.startTime}–${day.endTime}` : "정하지 않음", known: hasTime },
    { icon: MapPin, label: "장소", value: `${day.items.length}곳`, known: true },
    { icon: Hourglass, label: "머무는 시간", value: formatDuration(getTotalStayMinutes(day.items)), known: true },
    { icon: transportMode === "TRANSIT" ? Bus : Car, label: "이동", ...getTravelSummary(day, pendingTravel, calculating) },
  ];
}

type DaySummaryProps = { day: ScheduleDay; transportMode: TransportMode; pendingTravel: boolean; calculating: boolean };

/** 하루 요약(시간·장소 수·머무는 시간·이동)을 아이콘과 함께 2×2 칸으로 보여 준다. */
function DaySummary({ day, transportMode, pendingTravel, calculating }: DaySummaryProps) {
  return (
    <View className="mx-5 flex-row flex-wrap gap-y-3 rounded-md bg-muted p-3" testID="schedule-draft.day-summary">
      {getDaySummaryCells(day, transportMode, pendingTravel, calculating).map((cell) => {
        return (
          <View key={cell.label} accessible accessibilityLabel={`${cell.label} ${cell.value}`} className="w-1/2 flex-row items-center gap-2 pr-2">
            <View className="h-8 w-8 items-center justify-center rounded-sm bg-card">
              <Icon as={cell.icon} size={16} className="text-primary" />
            </View>
            <View className="flex-1">
              <Text className="text-caption text-muted-foreground">{cell.label}</Text>
              <Text className={cn("text-label tabular-nums", cell.known ? "text-foreground" : "text-muted-foreground")}>{cell.value}</Text>
            </View>
          </View>
        );
      })}
    </View>
  );
}

type DraftDayProps = {
  day: ScheduleDay;
  dayNumber: number;
  /** 고친 뒤 이동 시간을 아직 모르는 구간이 있으면 그 안내. 없으면 `null`. */
  pendingTravelMessage: string | null;
  /** 서버가 다시 계산하고 있다. */
  calculating: boolean;
  /** 이날 종료 시각을 늘린다. 종료 시각을 넘길 때 보여 준다. */
  onExtendEndTime: () => void;
  draft: ReadyScheduleDraft;
  places: SchedulePlaceLookup;
  locked: boolean;
  actions: DayActions;
  reorder: TimelineReorder;
  remove: TimelineRemove;
};

function DraftDay({ day, dayNumber, pendingTravelMessage, calculating, onExtendEndTime, draft, places, locked, actions, reorder, remove }: DraftDayProps) {
  const pendingTravel = pendingTravelMessage !== null;

  return (
    <View className="gap-3" testID={`schedule-draft.day-${day.date}`}>
      <Text accessibilityRole="header" className="px-5 text-label text-foreground">{`${dayNumber}일차 ${formatShortMonthDay(day.date)}`}</Text>
      <DaySummary day={day} transportMode={draft.transportMode} pendingTravel={pendingTravel} calculating={calculating} />
      {pendingTravelMessage === null ? null : (
        <Notice testID="schedule-draft.uncalculated">
          <Text accessibilityLiveRegion="polite" className="text-caption text-foreground-secondary">{pendingTravelMessage}</Text>
        </Notice>
      )}
      {day.warnings.length === 0 ? null : <WarningList warnings={day.warnings} testID="schedule-draft.day-warnings" />}
      {day.warnings.includes("END_TIME_EXCEEDED") ? <OverflowAction endTime={day.endTime} locked={locked} onExtend={onExtendEndTime} /> : null}
      {day.items.length === 0 ? (
        <Text className="px-5 text-caption text-muted-foreground">이날은 넣은 장소가 없어요</Text>
      ) : (
        <>
          <EditHelp />
          <ScheduleDayTimeline
            key={day.date}
            day={day}
            places={places}
            startPoint={draft.startPoint}
            endPoint={draft.endPoint}
            uncalculated={pendingTravel}
            calculating={calculating}

            renderItemActions={(item, name) => {
              return <StopActions item={item} name={name} locked={locked} actions={actions} />;
            }}

            reorder={reorder}
            remove={remove}
            testID="schedule-draft.timeline"
          />
        </>
      )}
    </View>
  );
}

/**
 * 그날이 종료 시각을 넘길 때의 안내와 "종료 시각 늘리기". 이대로면 다시 계산할 때 서버가 마지막 장소를 빼고(trimOverflow),
 * 저장은 SC002로 거절된다.
 */
function OverflowAction({ endTime, locked, onExtend }: { endTime: LocalTime | null; locked: boolean; onExtend: () => void }) {
  return (
    <View className="mx-5 gap-1" testID="schedule-draft.overflow">
      <Text className="text-caption text-foreground-secondary">
        {`이대로면 다시 계산할 때 마지막 장소가 일정에서 빠지고, 저장할 수 없어요.${endTime === null ? "" : ` 지금 종료 시각은 ${endTime}이에요.`}`}
      </Text>
      <UnplacedAction label="종료 시각 늘리기" accessibilityLabel="이날 종료 시각 늘리기" locked={locked} onPress={onExtend} testID="schedule-draft.overflow-extend" />
    </View>
  );
}

const EDIT_HELP: readonly { icon: LucideIcon; title: string; description: string }[] = [
  { icon: ArrowLeftRight, title: "다른 날로 옮기기", description: "다른 날에 넣거나 일정에서 빼요" },
  { icon: Hourglass, title: "머무는 시간", description: "눌러서 이 장소에 머무는 시간을 바꿔요" },
  { icon: GripVertical, title: "순서 바꾸기", description: "손잡이를 길게 누른 채 위아래로 끌어요" },
  { icon: Trash2, title: "빼기", description: "카드를 왼쪽으로 밀면 빼기 버튼이 나와요" },
];

/** "카드 편집 방법" 글자 버튼과 시트. 카드의 버튼·숨은 동작(길게 눌러 끌기, 밀어서 빼기)을 설명한다. */
function EditHelp() {
  const [open, setOpen] = useState(false);

  const close = () => {
    setOpen(false);
  };

  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="카드 편집 방법 보기"
        className="h-touch flex-row items-center gap-1 self-start px-5"

        onPress={() => {
          setOpen(true);
        }}

        testID="schedule-draft.edit-help"
      >
        <Icon as={Info} size={16} className="text-icon" />
        <Text className="text-label text-foreground-secondary">카드 편집 방법</Text>
        <Icon as={ChevronRight} size={16} className="text-icon" />
      </Pressable>
      <BottomSheet visible={open} onClose={close} closeLabel="카드 편집 방법 닫기" expandable={false}>
        <View className="gap-4 px-5 pb-4 pt-2" testID="schedule-draft.edit-help-sheet">
          <Text accessibilityRole="header" className="text-h3 text-foreground">카드 편집 방법</Text>
          {EDIT_HELP.map((item) => {
            return (
              <View key={item.title} accessible accessibilityLabel={`${item.title}. ${item.description}`} className="flex-row items-center gap-3">
                <View className="h-10 w-10 items-center justify-center rounded-md bg-muted">
                  <Icon as={item.icon} size={20} className="text-icon" />
                </View>
                <View className="flex-1 gap-0.5">
                  <Text className="text-label text-foreground">{item.title}</Text>
                  <Text className="text-caption text-foreground-secondary">{item.description}</Text>
                </View>
              </View>
            );
          })}
        </View>
      </BottomSheet>
    </>
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

function StopActions({ item, name, locked, actions }: { item: ScheduleItem; name: string; locked: boolean; actions: DayActions }) {
  return (
    <View className="flex-row items-center gap-2">
      <IconAction
        icon={ArrowLeftRight}
        label={`${name} 다른 날로 옮기기`}
        disabled={locked}

        onPress={() => {
          actions.openMove(item, name);
        }}

        testID={`schedule-draft.move-${item.id}`}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${name} 머무는 시간 ${formatDuration(item.stayMinutes)}, 바꾸기`}
        accessibilityState={{ disabled: locked }}
        disabled={locked}
        hitSlop={{ top: 6, bottom: 6 }}
        className="h-8 flex-row items-center gap-1 rounded-sm bg-muted px-2 active:bg-accent"

        onPress={() => {
          actions.openStay(item, name);
        }}

        testID={`schedule-draft.stay-${item.id}`}
      >
        <Icon as={Hourglass} size={14} className={locked ? "text-disabled-foreground" : "text-icon"} />
        <Text className="text-label-sm text-foreground tabular-nums">{`${item.stayMinutes}분`}</Text>
      </Pressable>
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

/**
 * 그사이 여행방(정보·후보·일정)이나 초안이 바뀌어 다시 계산·저장이 거절됐다(409 C004).
 * 덮어쓰지 않고, 반영되지 않았다는 것을 알린 뒤 바뀐 일정을 보거나 지금 여행방으로 초안을 다시 만들게 한다.
 */
function ConflictActions({ roomId, onRecreate }: { roomId: number; onRecreate: () => void }) {
  return (
    <>
      <Text accessibilityRole="alert" className="text-center text-caption text-destructive" testID="schedule-draft.conflict">
        그사이 일행이 여행방을 바꿔서 이 초안을 반영하지 못했어요. 바뀐 일정을 보고 다시 만들어 주세요.
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
        <Button size="lg" className="flex-1" onPress={onRecreate} testID="schedule-draft.recreate">
          <Text>다시 만들기</Text>
        </Button>
      </View>
    </>
  );
}

// #endregion
