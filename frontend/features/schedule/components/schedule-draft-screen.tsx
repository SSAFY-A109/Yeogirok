import { router } from "expo-router";
import { Info, TriangleAlert } from "lucide-react-native";
import { useState, type ReactNode } from "react";
import { ScrollView, View } from "react-native";

import { isApiError } from "@/api/errors";
import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useToast } from "@/components/ui/toast";
import { useRoom } from "@/features/room/api/queries";
import { TRANSPORT_MODE_LABEL, type RoomDetail } from "@/features/room/model";
import { useApplyScheduleDraft, useRoomSchedule, useScheduleDraft } from "@/features/schedule/api/queries";
import { DayChips, type DayChipItem } from "@/features/schedule/components/day-chips";
import { DayPickerSheet } from "@/features/schedule/components/day-picker-sheet";
import { ScheduleDayTimeline } from "@/features/schedule/components/schedule-day-timeline";
import { ScheduleProcessing } from "@/features/schedule/components/schedule-processing";
import { getDraftFailureMessage, getScheduleErrorMessage, isScheduleConflict, SCHEDULE_ERROR_FALLBACK } from "@/features/schedule/errors";
import {
  formatDuration,
  getScheduleChanges,
  hasUnplacedStops,
  SCHEDULE_PACE_LABEL,
  type ScheduleBody,
  type ScheduleDay,
  type ScheduleDraft,
} from "@/features/schedule/model";
import { formatShortMonthDay, type LocalDate } from "@/lib/date";
import { withDirectionParticle } from "@/lib/korean";

const TITLE = "일정 초안";
const NOT_FOUND_STATUS = 404;

type CompletedDraft = Extract<ScheduleDraft, { status: "COMPLETED" }>;

function goBack() {
  router.back();
}

/** 조건 화면으로 돌아간다. 조건 화면을 거치지 않고 들어왔으면 조건 화면을 새로 연다. */
function goToConditions(roomId: number) {
  if (router.canGoBack()) {
    router.back();
    return;
  }

  router.replace({ pathname: "/rooms/[roomId]/schedule/new", params: { roomId: String(roomId) } });
}

/** 여행방 상세의 일정 탭으로 돌아간다. 쌓인 조건·초안 화면은 닫는다. */
function goToRoomSchedule(roomId: number) {
  router.dismissTo({ pathname: "/rooms/[roomId]", params: { roomId: String(roomId), tab: "schedule" } });
}

/** 일정 초안(Figma W14, TRP-08·COM-05). 만드는 중 → 초안 검토 → 적용 순서다. 적용하기 전에는 여행방 일정을 바꾸지 않는다. */
export function ScheduleDraftScreen({ roomId, draftId }: { roomId: number | null; draftId: number | null }) {
  if (roomId === null || draftId === null) {
    return (
      <DraftMessage message="일정 초안을 찾을 수 없어요">
        <Button variant="outline" onPress={goBack} testID="schedule-draft.back">
          <Text>돌아가기</Text>
        </Button>
      </DraftMessage>
    );
  }

  return <DraftQuery roomId={roomId} draftId={draftId} />;
}

function DraftQuery({ roomId, draftId }: { roomId: number; draftId: number }) {
  const draftQuery = useScheduleDraft(roomId, draftId);
  const roomQuery = useRoom(roomId);
  const draft = draftQuery.data;

  const checkAgain = () => {
    void draftQuery.refetch();
  };

  if (draft === undefined) {
    if (!draftQuery.isError) {
      return <DraftLoading />;
    }

    if (isApiError(draftQuery.error) && draftQuery.error.status === NOT_FOUND_STATUS) {
      return (
        <DraftMessage message="일정 초안을 찾을 수 없어요">
          <Button variant="outline" onPress={goBack} testID="schedule-draft.back">
            <Text>돌아가기</Text>
          </Button>
        </DraftMessage>
      );
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
        <DraftMessage message={getDraftFailureMessage(draft.failureCode)}>
          <Button
            variant="outline"

            onPress={() => {
              goToConditions(roomId);
            }}

            testID="schedule-draft.retry-conditions"
          >
            <Text>조건 다시 정하기</Text>
          </Button>
        </DraftMessage>
      );
    case "COMPLETED":
      return <DraftReview roomId={roomId} draft={draft} room={roomQuery.data} />;
    case "UNKNOWN":
      return (
        <DraftMessage message="일정 초안 상태를 확인하지 못했어요. 잠시 후 다시 확인해 주세요.">
          <Button variant="outline" loading={draftQuery.isFetching} loadingLabel="확인하는 중" onPress={checkAgain} testID="schedule-draft.check-status">
            <Text>다시 확인</Text>
          </Button>
        </DraftMessage>
      );
  }
}

// #region 초안 검토

function toDayChipItems(days: readonly ScheduleDay[]): DayChipItem[] {
  return days.map((day, index) => {
    return { date: day.date, label: `${index + 1}일차`, caption: `${day.stops.length}곳`, hasWarning: hasUnplacedStops(day) };
  });
}

function DraftReview({ roomId, draft, room }: { roomId: number; draft: CompletedDraft; room: RoomDetail | undefined }) {
  const { schedule } = draft;
  const [selectedDate, setSelectedDate] = useState<LocalDate | undefined>(schedule.days[0]?.date);
  const [pickerOpen, setPickerOpen] = useState(false);
  const applyDraft = useApplyScheduleDraft();
  const toast = useToast();

  const selectedIndex = Math.max(
    schedule.days.findIndex((day) => {
      return day.date === selectedDate;
    }),
    0,
  );

  const selectedDay = schedule.days.at(selectedIndex);
  const chipItems = toDayChipItems(schedule.days);

  const apply = () => {
    applyDraft.mutate(
      { roomId, draftId: draft.id, baseVersion: draft.baseVersion },
      {
        onSuccess: () => {
          toast.show({ message: "일정을 적용했어요" });
          goToRoomSchedule(roomId);
        },
      },
    );
  };

  return (
    <Screen>
      <ScreenHeader title={TITLE} onBack={goBack} testID="schedule-draft.header" />
      <ScrollView className="flex-1" contentContainerClassName="gap-4 pb-6 pt-4" testID="schedule-draft.review">
        <DraftIntro schedule={schedule} />
        <TransportChangedNotice room={room} schedule={schedule} />
        {draft.baseVersion === null ? null : <ScheduleChangesNotice roomId={roomId} schedule={schedule} />}
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
            <DraftDay day={selectedDay} dayNumber={selectedIndex + 1} schedule={schedule} />
          </View>
        )}
      </ScrollView>
      <BottomCta className="border-t border-border">
        {isScheduleConflict(applyDraft.error) ? (
          <ConflictActions roomId={roomId} />
        ) : (
          <>
            {applyDraft.isError ? (
              <Text accessibilityRole="alert" className="text-center text-caption text-destructive" testID="schedule-draft.apply-error">
                {getScheduleErrorMessage(applyDraft.error, SCHEDULE_ERROR_FALLBACK.save)}
              </Text>
            ) : null}
            <Button
              size="lg"
              className="w-full"
              disabled={selectedDay === undefined}
              loading={applyDraft.isPending}
              loadingLabel="적용하는 중"
              onPress={apply}
              testID="schedule-draft.apply"
            >
              <Text>이 일정으로 정하기</Text>
            </Button>
          </>
        )}
      </BottomCta>
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
    </Screen>
  );
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

function DraftIntro({ schedule }: { schedule: ScheduleBody }) {
  const summary = getDraftSummary(schedule);

  return (
    <View className="gap-2 px-5">
      <Text accessibilityRole="header" className="text-h2 text-foreground">AI 일정 초안이에요</Text>
      <Text className="text-body-sm text-foreground-secondary">확인하고 정하면 여행방 일정이 돼요. 정하기 전에는 지금 일정이 바뀌지 않아요.</Text>
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

/** 초안을 만든 뒤 여행방 이동 수단이 바뀌면 이동 시간이 맞지 않는다(ScheduleTimeline 체크리스트). */
function TransportChangedNotice({ room, schedule }: { room: RoomDetail | undefined; schedule: ScheduleBody }) {
  if (room === undefined || room.transportMode === "UNKNOWN" || schedule.transportMode === "UNKNOWN" || room.transportMode === schedule.transportMode) {
    return null;
  }

  return (
    <Notice testID="schedule-draft.transport-changed">
      <Text className="text-caption text-foreground-secondary">
        {`여행방 이동 수단이 ${withDirectionParticle(TRANSPORT_MODE_LABEL[room.transportMode])} 바뀌었어요. 초안을 다시 만들어야 이동 시간이 맞아요.`}
      </Text>
    </Notice>
  );
}

function dayLabelsOf(schedule: ScheduleBody, dates: readonly LocalDate[]): string {
  return schedule.days.flatMap((day, index) => {
    return dates.includes(day.date) ? [`${index + 1}일차`] : [];
  }).join(", ");
}

/** 이미 일정이 있는 여행방이면 적용했을 때 달라지는 점을 알린다. 지금 일정을 못 받아도 검토는 막지 않는다. */
function ScheduleChangesNotice({ roomId, schedule }: { roomId: number; schedule: ScheduleBody }) {
  const currentQuery = useRoomSchedule(roomId);
  const current = currentQuery.data;
  const changes = current ? getScheduleChanges(current, schedule) : null;

  return (
    <Notice testID="schedule-draft.changes">
      <Text className="text-label text-foreground">정하면 지금 일정이 이 초안으로 바뀌어요</Text>
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

/** `10:00–18:00 · 5곳 · 머무는 시간 4시간 30분 · 이동 1시간 10분`. 이동 시간을 모르면 "정보 없음". */
function getDaySummary(day: ScheduleDay): string {
  const travel = day.totalTravelMinutes === null ? "이동 시간 정보 없음" : `이동 ${formatDuration(day.totalTravelMinutes)}`;
  return `${day.startTime}–${day.endTime} · ${day.stops.length}곳 · 머무는 시간 ${formatDuration(day.totalStayMinutes)} · ${travel}`;
}

function DraftDay({ day, dayNumber, schedule }: { day: ScheduleDay; dayNumber: number; schedule: ScheduleBody }) {
  return (
    <View className="gap-3" testID={`schedule-draft.day-${day.date}`}>
      <View className="gap-1 px-5">
        <Text accessibilityRole="header" className="text-label text-foreground">{`${dayNumber}일차 ${formatShortMonthDay(day.date)}`}</Text>
        <Text className="text-caption text-foreground-secondary tabular-nums">{getDaySummary(day)}</Text>
      </View>
      {day.stops.length === 0 ? (
        <Text className="px-5 text-caption text-muted-foreground">이날은 넣은 장소가 없어요</Text>
      ) : (
        <ScheduleDayTimeline day={day} transportMode={schedule.transportMode} testID="schedule-draft.timeline" />
      )}
      {hasUnplacedStops(day) ? <UnplacedStops day={day} /> : null}
    </View>
  );
}

/** 시간 안에 다 넣지 못한 장소와 서버가 정한 이유(명세 §7). */
function UnplacedStops({ day }: { day: ScheduleDay }) {
  return (
    <View accessibilityRole="alert" className="mx-5 gap-2 rounded-md bg-highlight-muted p-3" testID="schedule-draft.unplaced">
      <View className="flex-row items-center gap-2">
        <Icon as={TriangleAlert} size={16} className="text-highlight" />
        <Text className="flex-1 text-label text-highlight">{`시간 안에 넣지 못한 장소 ${day.unplaced.length}곳`}</Text>
      </View>
      {day.unplaced.map((stop) => {
        return (
          <View key={stop.id} className="gap-0.5 pl-6">
            <Text className="text-label text-foreground">{stop.name}</Text>
            <Text className="text-caption text-foreground-secondary">{stop.reason}</Text>
          </View>
        );
      })}
    </View>
  );
}

/** 그사이 일행이 일정을 바꿔 적용이 거절됐다(409 C008). 덮어쓰지 않고 바뀐 일정을 보거나 다시 만들게 한다. */
function ConflictActions({ roomId }: { roomId: number }) {
  return (
    <>
      <Text accessibilityRole="alert" className="text-center text-caption text-destructive" testID="schedule-draft.conflict">
        그사이 일행이 일정을 바꿔서 이 초안을 적용하지 못했어요. 바뀐 일정을 보고 다시 만들어 주세요.
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
            goToConditions(roomId);
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
      <View accessibilityRole="progressbar" accessibilityLabel="일정 초안을 불러오는 중" accessibilityState={{ busy: true }} className="gap-4 px-5 pt-4">
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

// #endregion
