import { MapPin } from "lucide-react-native";
import { FlatList, View } from "react-native";

import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import type { TripDateRange } from "@/features/room/components/trip-date-sheet";
import { getPlacesOutsideRange, type RoomSchedule, type ScheduleDay } from "@/features/schedule/model";
import { compareLocalDate, diffDays, formatTripLength, type LocalDate } from "@/lib/date";

/** 저장하면 일정에 생기는 변화(Figma W06-E2). */
export type ScheduleImpact = {
  /** 새 기간 밖으로 빠져 후보로 돌아가는 장소. */
  returningPlaces: ScheduleDay["places"];
  /** 장소가 빠지는 날의 차례(1일차부터). */
  returningDayNumbers: number[];
  /** 새 기간 안에 장소가 남는 날이 있는지. */
  keepsSomeDays: boolean;
  /** 여행 기간이 짧아지는지. */
  shortened: boolean;
  nextRange: TripDateRange | null;
  transportChanged: boolean;
};

type ScheduleImpactInput = {
  /** 받아 온 일정. 일정이 없거나 아직 모르면 `null`. */
  schedule: RoomSchedule | null;
  previousRange: TripDateRange | null;
  nextRange: TripDateRange | null;
  datesChanged: boolean;
  transportChanged: boolean;
};

function isOutsideRange(date: LocalDate, range: TripDateRange | null): boolean {
  return range === null || compareLocalDate(date, range.startDate) < 0 || compareLocalDate(date, range.endDate) > 0;
}

function getTripNights(range: TripDateRange): number {
  return diffDays(range.startDate, range.endDate);
}

/**
 * 저장 전에 확인이 필요한 변화를 계산한다. 빠지는 장소가 없고 이동 수단도 그대로면 `null`(바로 저장).
 * 날짜를 늘리기만 하면 빠지는 장소가 없으므로 `null`이다.
 */
export function getScheduleImpact({ schedule, previousRange, nextRange, datesChanged, transportChanged }: ScheduleImpactInput): ScheduleImpact | null {
  const days = datesChanged && schedule !== null ? schedule.days : [];
  const returningPlaces = datesChanged && schedule !== null ? getPlacesOutsideRange(schedule, nextRange) : [];

  if (returningPlaces.length === 0 && !transportChanged) {
    return null;
  }

  const returningDayNumbers = days
    .filter((day) => {
      return day.places.length > 0 && isOutsideRange(day.date, nextRange);
    })
    .map((day, index) => {
      return previousRange === null ? index + 1 : diffDays(previousRange.startDate, day.date) + 1;
    });

  return {
    returningPlaces,
    returningDayNumbers,

    keepsSomeDays: days.some((day) => {
      return day.places.length > 0 && !isOutsideRange(day.date, nextRange);
    }),

    shortened: previousRange !== null && nextRange !== null && getTripNights(nextRange) < getTripNights(previousRange),
    nextRange,
    transportChanged,
  };
}

type ImpactCopy = { title: string; description: string; confirmLabel: string };

/** 빠지는 날을 데이터에 맞춰 설명한다. 예: "당일치기로 줄면 2일차 장소는 후보로 돌아가고, 남은 일정의 이동 시간을 다시 계산해요." */
function describeReturningDays({ nextRange, keepsSomeDays, shortened, returningDayNumbers }: ScheduleImpact): string {
  if (nextRange === null) {
    return "날짜를 비우면 일정의 장소가 모두 후보로 돌아가요.";
  }

  if (!keepsSomeDays) {
    return "날짜를 바꾸면 일정의 장소가 모두 후보로 돌아가요.";
  }

  const lead = shortened ? `${formatTripLength(nextRange.startDate, nextRange.endDate)}로 줄면` : "날짜를 바꾸면";

  const dayLabels = returningDayNumbers
    .map((dayNumber) => {
      return `${dayNumber}일차`;
    })
    .join(", ");

  return `${lead} ${dayLabels} 장소는 후보로 돌아가고, 남은 일정의 이동 시간을 다시 계산해요.`;
}

function getImpactCopy(impact: ScheduleImpact): ImpactCopy {
  if (impact.returningPlaces.length === 0) {
    return { title: "이동 수단을 바꿀까요?", description: "이동 시간을 다시 계산해요.", confirmLabel: "이동 수단 바꾸기" };
  }

  return { title: "여행 날짜를 바꿀까요?", description: describeReturningDays(impact), confirmLabel: "날짜 바꾸기" };
}

type ScheduleImpactDialogProps = {
  /** 보여 줄 변화. `null`이면 닫혀 있다. */
  impact: ScheduleImpact | null;
  onCancel: () => void;
  onConfirm: () => void;
  loading: boolean;
};

/** 일정이 있는 여행방에서 날짜를 줄이거나 이동 수단을 바꿀 때 저장 전에 묻는 다이얼로그(Figma W06-E2). */
export function ScheduleImpactDialog({ impact, onCancel, onConfirm, loading }: ScheduleImpactDialogProps) {
  if (impact === null) {
    return null;
  }

  const copy = getImpactCopy(impact);

  return (
    <ConfirmDialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onCancel();
        }
      }}
      title={copy.title}
      description={copy.description}
      confirmLabel={copy.confirmLabel}
      onConfirm={onConfirm}
      loading={loading}
      loadingLabel="저장하는 중"
      testID="schedule-impact-dialog"
    >
      {impact.returningPlaces.length > 0 ? <ReturningPlaces places={impact.returningPlaces} /> : null}
    </ConfirmDialog>
  );
}

function ReturningPlaces({ places }: { places: ScheduleDay["places"] }) {
  return (
    <View className="gap-2 rounded-md bg-muted px-4 py-3" testID="schedule-impact-dialog.places">
      <Text className="text-label-sm text-muted-foreground">{`후보로 돌아가는 장소 ${places.length}곳`}</Text>
      <FlatList
        className="max-h-40"
        contentContainerClassName="gap-2"
        data={places}
        keyExtractor={(place) => {
          return String(place.id);
        }}
        renderItem={({ item }) => {
          return (
            <View className="flex-row items-center gap-2">
              <Icon as={MapPin} size={16} className="text-icon" />
              <Text className="flex-1 text-body-sm text-foreground">{item.name}</Text>
            </View>
          );
        }}
      />
    </View>
  );
}
