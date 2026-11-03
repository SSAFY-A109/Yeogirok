import { ChevronLeft, ChevronRight, X, type LucideIcon } from "lucide-react-native";
import { useState } from "react";
import { Pressable, View } from "react-native";

import { Badge } from "@/components/ui/badge";
import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import {
  compareLocalDate,
  formatDateRange,
  formatMonthDay,
  formatTripLength,
  getMonthGrid,
  localDateParts,
  todayLocalDate,
  type LocalDate,
} from "@/lib/date";
import { cn } from "@/lib/utils";

/** 여행 기간. 출발일과 돌아오는 날은 함께 정하거나 함께 비운다. */
export type TripDateRange = { startDate: LocalDate; endDate: LocalDate };

type YearMonth = { year: number; month: number };

type DateDraft = { startDate: LocalDate | null; endDate: LocalDate | null };

/** 달력 칸이 선택 기간에서 맡는 자리. `single`은 출발일만 골랐거나 당일치기인 날이다. */
type DayRangeRole = "start" | "end" | "single" | "between" | "none";

const WEEKDAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"] as const;
const SUNDAY_INDEX = 0;

function toYearMonth(date: LocalDate): YearMonth {
  const { year, month } = localDateParts(date);
  return { year, month };
}

function shiftMonth({ year, month }: YearMonth, delta: number): YearMonth {
  const index = year * 12 + (month - 1) + delta;
  return { year: Math.floor(index / 12), month: (index % 12) + 1 };
}

function isSameOrBeforeMonth(a: YearMonth, b: YearMonth): boolean {
  return a.year * 12 + a.month <= b.year * 12 + b.month;
}

/** 첫 탭은 출발일, 두 번째 탭은 돌아오는 날이다. 출발일보다 앞을 누르면 출발일을 다시 고르고, 같은 날을 두 번 누르면 당일치기다. */
function selectTripDay(draft: DateDraft, day: LocalDate): DateDraft {
  if (draft.startDate === null || draft.endDate !== null) {
    return { startDate: day, endDate: null };
  }

  if (compareLocalDate(day, draft.startDate) < 0) {
    return { startDate: day, endDate: null };
  }

  return { startDate: draft.startDate, endDate: day };
}

function getDayRangeRole(day: LocalDate, { startDate, endDate }: DateDraft): DayRangeRole {
  if (startDate === null) {
    return "none";
  }

  if (day === startDate) {
    return endDate === null || endDate === startDate ? "single" : "start";
  }

  if (endDate === null) {
    return "none";
  }

  if (day === endDate) {
    return "end";
  }

  return compareLocalDate(day, startDate) > 0 && compareLocalDate(day, endDate) < 0 ? "between" : "none";
}

type TripDateSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 시트를 열 때의 기간. 시트 안에서 고르는 값은 "선택 완료"를 눌러야 반영된다. */
  value: TripDateRange | null;
  /** "선택 완료"면 고른 기간, "날짜 미정"이면 `null`. */
  onConfirm: (range: TripDateRange | null) => void;
};

/**
 * 여행 날짜 캘린더 시트(Figma W06-C). 오늘 이전 날짜는 고를 수 없다. 최대 기간은 정해지지 않아 제한하지 않는다(TBD).
 * 고르던 값은 시트를 열 때마다 `value`에서 다시 시작하므로, 쓰는 쪽에서 열 때마다 `key`를 바꿔 새로 마운트한다.
 */
export function TripDateSheet({ open, onOpenChange, value, onConfirm }: TripDateSheetProps) {
  const today = todayLocalDate();
  const currentMonth = toYearMonth(today);
  const [draft, setDraft] = useState<DateDraft>({ startDate: value?.startDate ?? null, endDate: value?.endDate ?? null });
  const [visibleMonth, setVisibleMonth] = useState<YearMonth>(value === null ? currentMonth : toYearMonth(value.startDate));
  const canGoPrevMonth = !isSameOrBeforeMonth(visibleMonth, currentMonth);

  const close = () => {
    onOpenChange(false);
  };

  const confirm = () => {
    if (draft.startDate === null || draft.endDate === null) {
      return;
    }

    onConfirm({ startDate: draft.startDate, endDate: draft.endDate });
    close();
  };

  const clearDates = () => {
    onConfirm(null);
    close();
  };

  return (
    <BottomSheet open={open} onOpenChange={onOpenChange} accessibilityLabel="여행 날짜 선택" testID="trip-date-sheet">
      <View className="gap-4 px-5 pb-4 pt-2">
        <View className="flex-row items-center justify-between">
          <View className="gap-1">
            <Text accessibilityRole="header" className="text-h3 text-foreground">여행 날짜</Text>
            <Text className="text-caption text-foreground-secondary">출발일과 돌아오는 날을 차례로 눌러요</Text>
          </View>
          <SheetIconButton icon={X} iconSize={22} accessibilityLabel="닫기" onPress={close} testID="trip-date-sheet.close" />
        </View>
        <View className="flex-row items-center justify-between">
          <SheetIconButton
            icon={ChevronLeft}
            iconSize={20}
            accessibilityLabel="이전 달"
            disabled={!canGoPrevMonth}
            onPress={() => {
              setVisibleMonth(shiftMonth(visibleMonth, -1));
            }}
            testID="trip-date-sheet.prev-month"
          />
          <Text accessibilityLiveRegion="polite" className="text-h3 text-foreground">
            {`${visibleMonth.year}년 ${visibleMonth.month}월`}
          </Text>
          <SheetIconButton
            icon={ChevronRight}
            iconSize={20}
            accessibilityLabel="다음 달"
            onPress={() => {
              setVisibleMonth(shiftMonth(visibleMonth, 1));
            }}
            testID="trip-date-sheet.next-month"
          />
        </View>
        <MonthCalendar
          month={visibleMonth}
          today={today}
          draft={draft}
          onSelectDay={(day) => {
            setDraft(selectTripDay(draft, day));
          }}
        />
        <DraftSummary draft={draft} />
        <View className="flex-row gap-3">
          <Button variant="secondary" size="lg" className="flex-1" onPress={clearDates} testID="trip-date-sheet.clear">
            <Text>날짜 미정</Text>
          </Button>
          <Button
            size="lg"
            className="flex-1"
            disabled={draft.startDate === null || draft.endDate === null}
            onPress={confirm}
            testID="trip-date-sheet.confirm"
          >
            <Text>선택 완료</Text>
          </Button>
        </View>
      </View>
    </BottomSheet>
  );
}

// #region 달력

type MonthCalendarProps = {
  month: YearMonth;
  today: LocalDate;
  draft: DateDraft;
  onSelectDay: (day: LocalDate) => void;
};

function MonthCalendar({ month, today, draft, onSelectDay }: MonthCalendarProps) {
  const weeks = getMonthGrid(month.year, month.month);

  return (
    <View className="gap-1">
      <View className="flex-row" accessible={false} importantForAccessibility="no-hide-descendants">
        {WEEKDAY_LABELS.map((label, index) => {
          return (
            <View key={label} className="h-7 flex-1 items-center justify-center">
              <Text className={cn("text-caption", index === SUNDAY_INDEX ? "text-highlight" : "text-muted-foreground")}>{label}</Text>
            </View>
          );
        })}
      </View>
      {weeks.map((week, weekIndex) => {
        return (
          <View key={`${month.year}-${month.month}-${weekIndex}`} className="flex-row">
            {week.map((day, dayIndex) => {
              if (day === null) {
                return <View key={`empty-${dayIndex}`} className="h-11 flex-1" />;
              }

              return (
                <CalendarDay
                  key={day}
                  day={day}
                  isSunday={dayIndex === SUNDAY_INDEX}
                  disabled={compareLocalDate(day, today) < 0}
                  role={getDayRangeRole(day, draft)}
                  onPress={() => {
                    onSelectDay(day);
                  }}
                />
              );
            })}
          </View>
        );
      })}
    </View>
  );
}

type CalendarDayProps = {
  day: LocalDate;
  isSunday: boolean;
  disabled: boolean;
  role: DayRangeRole;
  onPress: () => void;
};

function CalendarDay({ day, isSunday, disabled, role, onPress }: CalendarDayProps) {
  const isEdge = role === "start" || role === "end" || role === "single";

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={formatMonthDay(day)}
      accessibilityState={{ selected: role !== "none", disabled }}
      className="h-11 flex-1 items-center justify-center"
      disabled={disabled}
      onPress={onPress}
      testID={`trip-date-sheet.day-${day}`}
    >
      {role === "start" ? <View className="absolute right-0 top-0.5 h-10 w-1/2 bg-accent" /> : null}
      {role === "end" ? <View className="absolute left-0 top-0.5 h-10 w-1/2 bg-accent" /> : null}
      {role === "between" ? <View className="absolute inset-x-0 top-0.5 h-10 bg-accent" /> : null}
      <View className={cn("size-10 items-center justify-center rounded-full", isEdge && "bg-primary")}>
        <Text className={getDayTextClass({ isEdge, isSunday, disabled })}>{localDateParts(day).day}</Text>
      </View>
    </Pressable>
  );
}

function getDayTextClass({ isEdge, isSunday, disabled }: { isEdge: boolean; isSunday: boolean; disabled: boolean }): string {
  if (isEdge) {
    return "text-label text-primary-foreground";
  }

  if (disabled) {
    return "text-body-sm text-disabled-foreground opacity-50";
  }

  return isSunday ? "text-body-sm text-highlight" : "text-body-sm text-foreground";
}

// #endregion

// #region 요약·버튼

function DraftSummary({ draft }: { draft: DateDraft }) {
  if (draft.startDate === null) {
    return null;
  }

  const rangeText = draft.endDate === null
    ? formatMonthDay(draft.startDate)
    : formatDateRange(draft.startDate, draft.endDate, { repeatMonth: true });

  return (
    <View accessibilityLiveRegion="polite" className="flex-row items-center gap-2 rounded-md bg-muted p-4" testID="trip-date-sheet.summary">
      <Text className="flex-1 text-label text-foreground">{rangeText}</Text>
      {draft.endDate === null ? null : (
        <Badge className="self-center">
          <Text className="text-primary">{formatTripLength(draft.startDate, draft.endDate)}</Text>
        </Badge>
      )}
    </View>
  );
}

type SheetIconButtonProps = {
  icon: LucideIcon;
  iconSize: number;
  accessibilityLabel: string;
  disabled?: boolean;
  onPress: () => void;
  testID: string;
};

/** 시트 안의 40×40 아이콘 버튼. 터치 영역은 `hitSlop`으로 44를 맞춘다. */
function SheetIconButton({ icon, iconSize, accessibilityLabel, disabled = false, onPress, testID }: SheetIconButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={{ disabled }}
      className={cn("size-10 items-center justify-center rounded-full active:bg-accent", disabled && "opacity-50")}
      disabled={disabled}
      hitSlop={2}
      onPress={onPress}
      testID={testID}
    >
      <Icon as={icon} size={iconSize} className={disabled ? "text-disabled-foreground" : "text-icon"} />
    </Pressable>
  );
}

// #endregion
