import { router } from "expo-router";
import { Clock } from "lucide-react-native";
import { useState, type ReactNode } from "react";
import { Pressable, ScrollView, View } from "react-native";

import { BottomCta } from "@/components/ui/bottom-cta";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useRoom } from "@/features/room/api/queries";
import { getRoomErrorMessage, ROOM_ERROR_FALLBACK } from "@/features/room/errors";
import { hasTripDates, MIN_PLACES_FOR_SCHEDULE, type RoomDetail } from "@/features/room/model";
import { useCreateScheduleDraft, useRoomSchedule } from "@/features/schedule/api/queries";
import { TimePickerSheet } from "@/features/schedule/components/time-picker-sheet";
import { getScheduleErrorMessage, SCHEDULE_ERROR_FALLBACK } from "@/features/schedule/errors";
import { getInitialDayTimes, getTripDates, isValidDayTimeRange, type DayTimeRange, type LocalTime, type RoomSchedule } from "@/features/schedule/model";
import { goToScheduleDraft } from "@/features/schedule/navigation";
import { formatShortMonthDay, type LocalDate } from "@/lib/date";
import { cn } from "@/lib/utils";

const TITLE = "일정 만들기";

type TimeTarget = { date: LocalDate; edge: "startTime" | "endTime" };

function goBack() {
  router.back();
}

type ScheduleConditionsScreenProps = {
  /** 검증한 라우트 파라미터. 잘못된 id면 `null`. */
  roomId: number | null;
  /** 저장된 일정의 날짜별 장소를 이어받는 초안을 만든다("일정 조정하기"에서 시각 조건이 없어 넘어온 경우). */
  keepSaved: boolean;
};

/** 일정 조건(Figma W13, TRP-08·10). 날마다 시작·종료 시각을 정해 일정 초안을 만든다(API-037). 하루 일정 밀도는 서버가 받지 않는다. */
export function ScheduleConditionsScreen({ roomId, keepSaved }: ScheduleConditionsScreenProps) {
  return roomId === null ? <ConditionsMessage message="여행방을 찾을 수 없어요" /> : <ConditionsQuery roomId={roomId} keepSaved={keepSaved} />;
}

function ConditionsQuery({ roomId, keepSaved }: { roomId: number; keepSaved: boolean }) {
  const roomQuery = useRoom(roomId);

  if (roomQuery.data) {
    return <ConditionsGate room={roomQuery.data} keepSaved={keepSaved} />;
  }

  if (roomQuery.isError) {
    return (
      <ConditionsMessage message={getRoomErrorMessage(roomQuery.error, ROOM_ERROR_FALLBACK.load)}>
        <Button
          variant="outline"
          loading={roomQuery.isFetching}
          loadingLabel="불러오는 중"

          onPress={() => {
            void roomQuery.refetch();
          }}

          testID="schedule-conditions.retry"
        >
          <Text>다시 시도</Text>
        </Button>
      </ConditionsMessage>
    );
  }

  return <ConditionsLoading label="여행방을 불러오는 중" />;
}

function ConditionsLoading({ label }: { label: string }) {
  return (
    <Screen>
      <ScreenHeader title={TITLE} onBack={goBack} testID="schedule-conditions.header" />
      <View accessibilityRole="progressbar" accessibilityLabel={label} accessibilityState={{ busy: true }} className="gap-4 px-5 pt-4">
        <Skeleton className="h-8 w-56 rounded-xs" />
        <Skeleton className="h-4 w-72 rounded-xs" />
        <Skeleton className="h-32 w-full rounded-md" />
      </View>
    </Screen>
  );
}

/** 날짜가 없거나 후보 장소가 모자라면(딥링크 등) 조건 화면 대신 이유와 할 일을 보여 준다. */
function ConditionsGate({ room, keepSaved }: { room: RoomDetail; keepSaved: boolean }) {
  if (!hasTripDates(room)) {
    return (
      <ConditionsMessage message="여행 날짜를 정하면 일정을 만들 수 있어요">
        <Button
          variant="outline"

          onPress={() => {
            router.replace({ pathname: "/rooms/[roomId]/edit", params: { roomId: String(room.id) } });
          }}

          testID="schedule-conditions.set-dates"
        >
          <Text>날짜 정하기</Text>
        </Button>
      </ConditionsMessage>
    );
  }

  if (room.placeCount < MIN_PLACES_FOR_SCHEDULE) {
    return (
      <ConditionsMessage message={`후보 장소를 ${MIN_PLACES_FOR_SCHEDULE}곳 이상 담아 주세요`}>
        <Button variant="outline" onPress={goBack} testID="schedule-conditions.back">
          <Text>여행방으로 돌아가기</Text>
        </Button>
      </ConditionsMessage>
    );
  }

  const dates = getTripDates(room.startDate, room.endDate);
  return keepSaved && room.hasSchedule ? <SavedConditions room={room} dates={dates} /> : <ConditionsForm room={room} dates={dates} saved={null} />;
}

/** 저장된 일정을 받아 그 시각 조건을 처음 값으로 쓰고, 초안이 날짜별 장소를 이어받게 한다. */
function SavedConditions({ room, dates }: { room: RoomDetail; dates: LocalDate[] }) {
  const savedQuery = useRoomSchedule(room.id);

  if (savedQuery.data !== undefined) {
    return <ConditionsForm room={room} dates={dates} saved={savedQuery.data} />;
  }

  if (savedQuery.isError) {
    return (
      <ConditionsMessage message={getScheduleErrorMessage(savedQuery.error)}>
        <Button
          variant="outline"
          loading={savedQuery.isFetching}
          loadingLabel="불러오는 중"

          onPress={() => {
            void savedQuery.refetch();
          }}

          testID="schedule-conditions.saved-retry"
        >
          <Text>다시 시도</Text>
        </Button>
      </ConditionsMessage>
    );
  }

  return <ConditionsLoading label="지금 일정을 불러오는 중" />;
}

function ConditionsForm({ room, dates, saved }: { room: RoomDetail; dates: LocalDate[]; saved: RoomSchedule | null }) {
  const [days, setDays] = useState<DayTimeRange[]>(() => {
    return getInitialDayTimes(dates, saved);
  });

  const [timeTarget, setTimeTarget] = useState<TimeTarget | null>(null);
  const createDraft = useCreateScheduleDraft();

  const allValid = days.every(isValidDayTimeRange);

  const targetDay = timeTarget === null ? undefined : days.find((day) => {
    return day.date === timeTarget.date;
  });

  const targetIndex = targetDay === undefined ? -1 : days.indexOf(targetDay);
  const timeTitle = timeTarget === null || targetDay === undefined ? null : `${targetIndex + 1}일차 ${timeTarget.edge === "startTime" ? "시작" : "종료"}`;

  const setTime = (time: LocalTime) => {
    if (timeTarget === null) {
      return;
    }

    createDraft.reset();

    setDays((current) => {
      return current.map((day) => {
        return day.date === timeTarget.date ? { ...day, [timeTarget.edge]: time } : day;
      });
    });
  };

  const submit = () => {
    createDraft.mutate(
      { roomId: room.id, days, keepSchedule: saved },
      {
        onSuccess: ({ draftKey }) => {
          goToScheduleDraft(room.id, draftKey);
        },
      },
    );
  };

  return (
    <Screen>
      <ScreenHeader title={TITLE} onBack={goBack} testID="schedule-conditions.header" />
      <ScrollView className="flex-1" contentContainerClassName="gap-6 px-5 pb-6 pt-4" testID="schedule-conditions">
        <View className="gap-2">
          <Text accessibilityRole="header" className="text-h2 text-foreground">몇 시부터 몇 시까지 다닐까요?</Text>
          <Text className="text-body-sm text-foreground-secondary">{`날마다 시작/종료 시각을 정하면 담은 장소 ${room.placeCount}곳으로 순서와 시간을 계산해요`}</Text>
          {saved === null ? null : <Text className="text-caption text-muted-foreground">지금 일정에 넣은 장소는 같은 날에 두고 계산해요</Text>}
        </View>
        <View className="gap-2">
          <View className="rounded-md border border-border">
            {days.map((day, index) => {
              const valid = isValidDayTimeRange(day);

              return (
                <View key={day.date} className={cn("gap-1 px-4 py-3", index > 0 && "border-t border-border")} testID={`schedule-conditions.day-${day.date}`}>
                  <View className="flex-row items-center gap-3">
                    <View className="flex-1">
                      <Text className="text-label text-foreground">{`${index + 1}일차`}</Text>
                      <Text className="text-caption text-foreground-secondary">{formatShortMonthDay(day.date)}</Text>
                    </View>
                    <TimeButton
                      label={`${index + 1}일차 시작 시각`}
                      time={day.startTime}
                      invalid={!valid}

                      onPress={() => {
                        setTimeTarget({ date: day.date, edge: "startTime" });
                      }}

                      testID={`schedule-conditions.start-${day.date}`}
                    />
                    <Text accessible={false} className="text-body-sm text-muted-foreground">–</Text>
                    <TimeButton
                      label={`${index + 1}일차 종료 시각`}
                      time={day.endTime}
                      invalid={!valid}

                      onPress={() => {
                        setTimeTarget({ date: day.date, edge: "endTime" });
                      }}

                      testID={`schedule-conditions.end-${day.date}`}
                    />
                  </View>
                  {valid ? null : <Text accessibilityRole="alert" className="text-caption text-destructive">종료 시각은 시작 시각보다 늦어야 해요</Text>}
                </View>
              );
            })}
          </View>
          <Text className="text-caption text-muted-foreground">시각을 누르면 시간 선택기가 열려요</Text>
        </View>
      </ScrollView>
      <BottomCta className="border-t border-border">
        {createDraft.isError ? (
          <Text accessibilityRole="alert" className="text-center text-caption text-destructive" testID="schedule-conditions.error">
            {getScheduleErrorMessage(createDraft.error, SCHEDULE_ERROR_FALLBACK.draft)}
          </Text>
        ) : null}
        <Button
          size="lg"
          className="w-full"
          disabled={!allValid}
          loading={createDraft.isPending}
          loadingLabel="초안을 요청하는 중"
          onPress={submit}
          testID="schedule-conditions.submit"
        >
          <Text>일정 초안 만들기</Text>
        </Button>
      </BottomCta>
      <TimePickerSheet
        title={timeTitle}
        value={targetDay?.[timeTarget?.edge ?? "startTime"] ?? "10:00"}
        onSelect={setTime}

        onClose={() => {
          setTimeTarget(null);
        }}
      />
    </Screen>
  );
}

type TimeButtonProps = { label: string; time: LocalTime; invalid: boolean; onPress: () => void; testID: string };

function TimeButton({ label, time, invalid, onPress, testID }: TimeButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${label} ${time}`}
      accessibilityHint="누르면 시간 선택기가 열려요"
      className={cn("h-11 flex-row items-center gap-1.5 rounded-sm border bg-card px-3 active:bg-muted", invalid ? "border-destructive" : "border-input")}
      onPress={onPress}
      testID={testID}
    >
      <Icon as={Clock} size={16} className="text-icon" />
      <Text className="text-label text-foreground tabular-nums">{time}</Text>
    </Pressable>
  );
}

function ConditionsMessage({ message, children }: { message: string; children?: ReactNode }) {
  return (
    <Screen>
      <ScreenHeader title={TITLE} onBack={goBack} testID="schedule-conditions.header" />
      <View className="flex-1 items-center justify-center gap-4 px-5 pb-12" testID="schedule-conditions.message">
        <Text className="text-center text-body-sm text-foreground-secondary">{message}</Text>
        {children}
      </View>
    </Screen>
  );
}
