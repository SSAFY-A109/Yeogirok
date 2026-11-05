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
import { useCreateScheduleDraft } from "@/features/schedule/api/queries";
import { TimePickerSheet } from "@/features/schedule/components/time-picker-sheet";
import { getScheduleErrorMessage, SCHEDULE_ERROR_FALLBACK } from "@/features/schedule/errors";
import {
  getDefaultDayTimes,
  isValidDayTimeRange,
  SCHEDULE_PACE_LABEL,
  SCHEDULE_PACES,
  toCreateDraftRequest,
  type DayTimeRange,
  type LocalTime,
  type SchedulePaceDto,
} from "@/features/schedule/model";
import { useIdempotencyKeyFor } from "@/hooks/use-idempotency-key";
import { addDays, diffDays, formatShortMonthDay, type LocalDate } from "@/lib/date";
import { cn } from "@/lib/utils";

const TITLE = "일정 만들기";
const SEGMENT_HIT_SLOP = { top: 4, bottom: 4 };

type TimeTarget = { date: LocalDate; edge: "startTime" | "endTime" };

function tripDates(startDate: LocalDate, endDate: LocalDate): LocalDate[] {
  return Array.from({ length: diffDays(startDate, endDate) + 1 }, (_, index) => {
    return addDays(startDate, index);
  });
}

function goBack() {
  router.back();
}

/** 일정 조건(Figma W13, TRP-08·10). 날마다 시작·종료 시각과 하루 일정 밀도를 정해 AI 초안을 만든다. */
export function ScheduleConditionsScreen({ roomId }: { roomId: number | null }) {
  return roomId === null ? <ConditionsMessage message="여행방을 찾을 수 없어요" /> : <ConditionsQuery roomId={roomId} />;
}

function ConditionsQuery({ roomId }: { roomId: number }) {
  const roomQuery = useRoom(roomId);

  if (roomQuery.data) {
    return <ConditionsGate room={roomQuery.data} />;
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

  return (
    <Screen>
      <ScreenHeader title={TITLE} onBack={goBack} testID="schedule-conditions.header" />
      <View accessibilityRole="progressbar" accessibilityLabel="여행방을 불러오는 중" accessibilityState={{ busy: true }} className="gap-4 px-5 pt-4">
        <Skeleton className="h-8 w-56 rounded-xs" />
        <Skeleton className="h-4 w-72 rounded-xs" />
        <Skeleton className="h-32 w-full rounded-md" />
      </View>
    </Screen>
  );
}

/** 날짜가 없거나 후보 장소가 모자라면(딥링크 등) 조건 화면 대신 이유와 할 일을 보여 준다. */
function ConditionsGate({ room }: { room: RoomDetail }) {
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

  return <ConditionsForm room={room} dates={tripDates(room.startDate, room.endDate)} />;
}

function ConditionsForm({ room, dates }: { room: RoomDetail; dates: LocalDate[] }) {
  const [days, setDays] = useState<DayTimeRange[]>(() => {
    return getDefaultDayTimes(dates);
  });

  const [pace, setPace] = useState<SchedulePaceDto>("NORMAL");
  const [timeTarget, setTimeTarget] = useState<TimeTarget | null>(null);
  const createDraft = useCreateScheduleDraft();
  const keyFor = useIdempotencyKeyFor();

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
    const request = toCreateDraftRequest(pace, days);

    createDraft.mutate(
      { roomId: room.id, request, idempotencyKey: keyFor(JSON.stringify(request)) },
      {
        onSuccess: ({ draftId }) => {
          router.push({ pathname: "/rooms/[roomId]/schedule/draft", params: { roomId: String(room.id), draftId: String(draftId) } });
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
        <View className="gap-3">
          <Text accessibilityRole="header" className="text-label text-foreground">하루 일정</Text>
          <View accessibilityRole="radiogroup" className="flex-row rounded-sm bg-muted p-1">
            {SCHEDULE_PACES.map((value) => {
              const selected = value === pace;

              return (
                <Pressable
                  key={value}
                  accessibilityRole="radio"
                  accessibilityState={{ checked: selected }}
                  className={cn("h-9 flex-1 items-center justify-center rounded-xs", selected && "border border-border bg-card")}
                  hitSlop={SEGMENT_HIT_SLOP}

                  onPress={() => {
                    createDraft.reset();
                    setPace(value);
                  }}

                  testID={`schedule-conditions.pace-${value.toLowerCase()}`}
                >
                  <Text className={selected ? "text-label text-foreground" : "text-body-sm text-foreground-secondary"}>{SCHEDULE_PACE_LABEL[value]}</Text>
                </Pressable>
              );
            })}
          </View>
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
