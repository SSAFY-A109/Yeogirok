import { router } from "expo-router";
import { ArrowLeftRight, ChevronDown, ChevronRight, ChevronUp, GripVertical, Hourglass, Info, TriangleAlert, type LucideIcon } from "lucide-react-native";
import { useState, type ReactNode } from "react";
import { Modal, Pressable, ScrollView, View } from "react-native";

import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { MapViewToggle } from "@/features/room/components/map-view-toggle";
import { PlaceRouteMap } from "@/features/room/components/place-route-map";
import { TRANSPORT_MODE_LABEL, type RoomDetail } from "@/features/room/model";
import { SCHEDULE_DRAFT_MAX_WAIT_MS, useAdjustScheduleDraft, useRoomSchedule, useSaveSchedule } from "@/features/schedule/api/queries";
import { DayChips, type DayChipItem } from "@/features/schedule/components/day-chips";
import { DayPickerSheet } from "@/features/schedule/components/day-picker-sheet";
import { MoveStopSheet, type MoveDayOption, type MoveStopTarget } from "@/features/schedule/components/move-stop-sheet";
import { ScheduleDayTimeline } from "@/features/schedule/components/schedule-day-timeline";
import { StayTimeSheet, type StayTarget } from "@/features/schedule/components/stay-time-sheet";
import { getScheduleErrorMessage, isScheduleConflict, SCHEDULE_ERROR_FALLBACK } from "@/features/schedule/errors";
import {
  formatDuration,
  getEditedDates,
  getItemTimeLabel,
  getPlaceName,
  getScheduleChanges,
  getScheduleWarningMessage,
  getTotalStayMinutes,
  getTotalTravelMinutes,
  isSameLayoutOrder,
  moveItemToDay,
  moveItemWithinDay,
  setItemStay,
  toAdjustDraftRequest,
  toDraftLayout,
  toSaveScheduleRequest,
  toScheduleMapStops,
  toUncalculatedDay,
  unassignItem,
  type DraftLayout,
  type DraftLayoutDay,
  type DraftLayoutItem,
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
  startReorder: () => void;
};

function goBack() {
  router.back();
}

function dayLabelOf(index: number): string {
  return `${index + 1}일차`;
}

/**
 * 일정 초안 검토·조정(Figma W14·W14-M·W14-T, TRP-08·09).
 * 다른 날로 옮기기·빼기·머무는 시간·순서 바꾸기는 화면의 배치만 고친다(서버 호출 없음). 고친 날은 시각과 이동 시간이
 * 낡으므로 보여 주지 않고, "다시 계산"(API-084)으로 서버 계산을 받는다. 저장(API-039)은 다시 계산하지 않아도 할 수 있다.
 * 저장하기 전에는 여행방 일정을 바꾸지 않는다.
 */
export function ScheduleEditor({ roomId, draftKey, room, draft, places, adjusting, progress, onRecreate }: ScheduleEditorProps) {
  const [selectedDate, setSelectedDate] = useState(draft.conditions.at(0)?.date);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [edits, setEdits] = useState<LocalEdits | null>(null);
  const [reorderLayout, setReorderLayout] = useState<DraftLayout | null>(null);
  const [moveTarget, setMoveTarget] = useState<MoveStopTarget | null>(null);
  const [stayTarget, setStayTarget] = useState<StayTarget | null>(null);
  const [recalculatedFrom, setRecalculatedFrom] = useState<number | null>(null);
  const [mapOpen, setMapOpen] = useState(false);
  const adjustDraft = useAdjustScheduleDraft();
  const saveDraft = useSaveSchedule();
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

  const selectedDay = selectedLayoutDay === undefined ? undefined : calculatedDay ?? toUncalculatedDay(selectedLayoutDay);

  const chipItems: DayChipItem[] = layout.days.map((day, index) => {
    const warnings = editedDates.includes(day.date) ? [] : draft.schedule.days.find((item) => {
      return item.date === day.date;
    })?.warnings ?? [];

    return { date: day.date, label: dayLabelOf(index), caption: `${day.items.length}곳`, hasWarning: warnings.length > 0 };
  });

  const moveDays: MoveDayOption[] = layout.days.map((day, index) => {
    return { date: day.date, label: dayLabelOf(index), stopCount: day.items.length };
  });

  const reorderDay = reorderLayout?.days.find((day) => {
    return day.date === selectedLayoutDay?.date;
  });

  const labelOfDate = (date: LocalDate) => {
    return moveDays.find((day) => {
      return day.date === date;
    })?.label ?? "";
  };

  const applyEdit = (next: DraftLayout) => {
    adjustDraft.reset();
    saveDraft.reset();
    setRecalculatedFrom(null);
    setEdits({ draftVersion: draft.draftVersion, layout: next });
  };

  const applyUndoableEdit = (next: DraftLayout, message: string) => {
    const previous = layout;
    applyEdit(next);

    toast.show({
      message,

      action: {
        label: "되돌리기",

        onPress: () => {
          applyEdit(previous);
        },
      },
    });
  };

  const moveItem = (target: MoveStopTarget, toDate: LocalDate) => {
    setMoveTarget(null);
    applyUndoableEdit(moveItemToDay(layout, target.itemId, toDate), `${withDirectionParticle(labelOfDate(toDate))} 옮겼어요`);
  };

  const removeFromSchedule = (target: MoveStopTarget) => {
    setMoveTarget(null);
    applyUndoableEdit(unassignItem(layout, target.itemId), `${withObjectParticle(target.name)} 후보 장소로 돌려보냈어요`);
  };

  const changeStay = (target: StayTarget, stayMinutes: number) => {
    setStayTarget(null);
    applyEdit(setItemStay(layout, target.itemId, stayMinutes));
  };

  const recalculate = () => {
    saveDraft.reset();
    setRecalculatedFrom(null);

    adjustDraft.mutate(
      { roomId, draftKey, request: toAdjustDraftRequest(draft, serverLayout, layout) },
      {
        onSuccess: () => {
          setRecalculatedFrom(draft.draftVersion);
        },
      },
    );
  };

  const save = () => {
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

  /** 서버가 계산한 시각. 고친 날이거나 서버가 모르면 `null`. */
  const timeTextOf = (item: ScheduleItem) => {
    return selectedEdited || item.arrivalTime === null ? null : getItemTimeLabel(item);
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

    startReorder: () => {
      setReorderLayout(layout);
    },
  };

  const openMoveUnassigned = (item: DraftLayoutItem) => {
    setMoveTarget({ itemId: item.itemId, name: getPlaceName(places, item.roomPlaceId), fromDate: null, detail: "지금 일정에 넣지 않은 장소" });
  };

  const renderActions = () => {
    if (isScheduleConflict(saveDraft.error) || isScheduleConflict(adjustDraft.error)) {
      return <ConflictActions roomId={roomId} onRecreate={onRecreate} />;
    }

    if (reorderLayout !== null) {
      return (
        <View className="w-full flex-row gap-2">
          <Button
            variant="outline"
            size="lg"
            className="flex-1"

            onPress={() => {
              setReorderLayout(null);
            }}

            testID="schedule-draft.reorder-cancel"
          >
            <Text>취소</Text>
          </Button>
          <Button
            size="lg"
            className="flex-1"
            disabled={isSameLayoutOrder(reorderLayout, layout)}

            onPress={() => {
              applyEdit(reorderLayout);
              setReorderLayout(null);
            }}

            testID="schedule-draft.reorder-apply"
          >
            <Text>순서 적용</Text>
          </Button>
        </View>
      );
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
        {reorderDay === undefined ? (
          <DraftDay
            day={selectedDay}
            dayNumber={selectedIndex + 1}
            uncalculated={selectedEdited}
            draft={draft}
            places={places}
            locked={editingLocked}
            actions={dayActions}
          />
        ) : (
          <ReorderDay
            day={reorderDay}
            dayNumber={selectedIndex + 1}
            places={places}

            onMove={(itemId, direction) => {
              setReorderLayout((current) => {
                return current === null ? current : moveItemWithinDay(current, itemId, direction);
              });
            }}
          />
        )}
      </View>
    );
  };

  // 순서를 바꾸는 동안에는 지도로 넘어가지 않는다. 지도는 고른 날의 지금 순서(저장 전 편집 포함)를 보여 준다.
  const mapStops = selectedDay === undefined || reorderDay !== undefined ? [] : toScheduleMapStops(selectedDay.items, places);

  const closeMap = () => {
    setMapOpen(false);
  };

  return (
    <Screen>
      <ScreenHeader title={adjusting ? "일정 조정" : "일정 초안"} onBack={goBack} testID="schedule-draft.header" />
      <View className="flex-1">
        <ScrollView className="flex-1" contentContainerClassName="gap-4 pb-16 pt-4" testID="schedule-draft.review">
          <EditorIntro adjusting={adjusting} draft={draft} />
          {room?.hasSchedule ? <ScheduleChangesNotice roomId={roomId} layout={layout} places={places} /> : null}
          {editedDates.length === 0 && draft.schedule.warnings.length > 0 ? <WarningList warnings={draft.schedule.warnings} testID="schedule-draft.warnings" /> : null}
          {layout.unassigned.length === 0 || reorderLayout !== null ? null : (
            <UnassignedItems items={layout.unassigned} places={places} locked={editingLocked} onMove={openMoveUnassigned} />
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

type UnassignedItemsProps = {
  items: readonly DraftLayoutItem[];
  places: SchedulePlaceLookup;
  locked: boolean;
  onMove: (item: DraftLayoutItem) => void;
};

/** 일정에 넣지 않은 장소. 서버가 시간 안에 넣지 못했거나 사용자가 뺀 항목이다(서버는 이유를 주지 않는다). */
function UnassignedItems({ items, places, locked, onMove }: UnassignedItemsProps) {
  return (
    <View className="mx-5 gap-3 rounded-md bg-highlight-muted p-3" testID="schedule-draft.unplaced">
      <View className="flex-row items-center gap-2">
        <Icon as={TriangleAlert} size={16} className="text-highlight" />
        <Text accessibilityRole="alert" className="flex-1 text-label text-highlight">{`일정에 넣지 않은 장소 ${items.length}곳`}</Text>
      </View>
      <Text className="pl-6 text-caption text-foreground-secondary">시간 안에 넣지 못했거나 일정에서 뺀 장소예요. 넣을 날을 고를 수 있어요.</Text>
      {items.map((item) => {
        const name = getPlaceName(places, item.roomPlaceId);

        return (
          <View key={item.itemId} className="gap-0.5 pl-6">
            <Text className="text-label text-foreground">{name}</Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`${name}, 넣을 날 고르기`}
              accessibilityState={{ disabled: locked }}
              disabled={locked}
              className="h-touch flex-row items-center gap-1 self-start"

              onPress={() => {
                onMove(item);
              }}

              testID={`schedule-draft.unplaced-move-${item.itemId}`}
            >
              <Text className={cn("text-label", locked ? "text-disabled-foreground" : "text-primary")}>넣을 날 고르기</Text>
              <Icon as={ChevronRight} size={16} className={locked ? "text-disabled-foreground" : "text-primary"} />
            </Pressable>
          </View>
        );
      })}
    </View>
  );
}

// #endregion

// #region 하루 일정과 조정

/** `10:00–18:00 · 5곳 · 머무는 시간 4시간 30분 · 이동 1시간 10분`. 고친 날은 이동 시간을 빼고, 서버가 모르는 구간이 있으면 "정보 없음". */
function getDaySummary(day: ScheduleDay, uncalculated: boolean): string {
  const parts: string[] = [];

  if (day.startTime !== null && day.endTime !== null) {
    parts.push(`${day.startTime}–${day.endTime}`);
  }

  parts.push(`${day.items.length}곳`);

  if (day.items.length > 0) {
    parts.push(`머무는 시간 ${formatDuration(getTotalStayMinutes(day.items))}`);
  }

  if (!uncalculated && day.legs.length > 0) {
    const travel = getTotalTravelMinutes(day.legs);
    parts.push(travel === null ? "이동 시간 정보 없음" : `이동 ${formatDuration(travel)}`);
  }

  return parts.join(" · ");
}

type DraftDayProps = {
  day: ScheduleDay;
  dayNumber: number;
  /** 고친 뒤 다시 계산하지 않은 날. */
  uncalculated: boolean;
  draft: ReadyScheduleDraft;
  places: SchedulePlaceLookup;
  locked: boolean;
  actions: DayActions;
};

function DraftDay({ day, dayNumber, uncalculated, draft, places, locked, actions }: DraftDayProps) {
  return (
    <View className="gap-3" testID={`schedule-draft.day-${day.date}`}>
      <View className="gap-1 px-5">
        <Text accessibilityRole="header" className="text-label text-foreground">{`${dayNumber}일차 ${formatShortMonthDay(day.date)}`}</Text>
        <Text className="text-caption text-foreground-secondary tabular-nums">{getDaySummary(day, uncalculated)}</Text>
      </View>
      {uncalculated ? (
        <Notice testID="schedule-draft.uncalculated">
          <Text className="text-caption text-foreground-secondary">이날은 고친 뒤 아직 계산하지 않았어요. 다시 계산하면 도착·출발 시각과 이동 시간을 볼 수 있어요.</Text>
        </Notice>
      ) : null}
      {day.warnings.length === 0 ? null : <WarningList warnings={day.warnings} testID="schedule-draft.day-warnings" />}
      {day.items.length === 0 ? (
        <Text className="px-5 text-caption text-muted-foreground">이날은 넣은 장소가 없어요</Text>
      ) : (
        <>
          <ActionLegend />
          <ScheduleDayTimeline
            day={day}
            places={places}
            startPoint={draft.startPoint}
            endPoint={draft.endPoint}
            uncalculated={uncalculated}

            renderItemActions={(item, name) => {
              return <StopActions item={item} name={name} locked={locked} actions={actions} />;
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
      <IconAction icon={GripVertical} label={`${name} 순서 바꾸기`} disabled={locked} onPress={actions.startReorder} testID={`schedule-draft.reorder-${item.id}`} />
    </View>
  );
}

type ReorderDayProps = {
  day: DraftLayoutDay;
  dayNumber: number;
  places: SchedulePlaceLookup;
  onMove: (itemId: number, direction: -1 | 1) => void;
};

/** 순서 바꾸기(W14 ⠿). 드래그 대신 위·아래 버튼으로 옮긴다. 시각은 다시 계산해야 맞으므로 이 동안에는 보여 주지 않는다. */
function ReorderDay({ day, dayNumber, places, onMove }: ReorderDayProps) {
  return (
    <View className="gap-3 px-5" testID="schedule-draft.reorder">
      <View className="gap-1">
        <Text accessibilityRole="header" className="text-label text-foreground">{`${dayNumber}일차 순서 바꾸기`}</Text>
        <Text className="text-caption text-foreground-secondary">위·아래 버튼으로 순서를 바꿔 적용한 뒤 다시 계산하면 시각과 이동 시간이 맞춰져요.</Text>
      </View>
      {day.items.length === 0 ? <Text className="text-caption text-muted-foreground">이날은 넣은 장소가 없어요</Text> : null}
      {day.items.map((item, index) => {
        const name = getPlaceName(places, item.roomPlaceId);

        return (
          <View key={item.itemId} className="flex-row items-center gap-3 rounded-md border border-border bg-card py-1 pl-3 pr-1" testID={`schedule-draft.reorder-row-${item.itemId}`}>
            <View className="h-6 w-6 items-center justify-center rounded-full bg-primary">
              <Text className="text-micro text-primary-foreground">{index + 1}</Text>
            </View>
            <View className="flex-1 gap-0.5 py-2">
              <Text className="text-label text-foreground">{name}</Text>
              <Text className="text-caption text-foreground-secondary">{`머무는 시간 ${formatDuration(item.stayMinutes)}`}</Text>
            </View>
            <IconAction
              icon={ChevronUp}
              label={`${name} 앞으로`}
              disabled={index === 0}

              onPress={() => {
                onMove(item.itemId, -1);
              }}

              testID={`schedule-draft.reorder-up-${item.itemId}`}
            />
            <IconAction
              icon={ChevronDown}
              label={`${name} 뒤로`}
              disabled={index === day.items.length - 1}

              onPress={() => {
                onMove(item.itemId, 1);
              }}

              testID={`schedule-draft.reorder-down-${item.itemId}`}
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
