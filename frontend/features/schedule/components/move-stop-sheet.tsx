import { Info } from "lucide-react-native";
import { useState } from "react";
import { Pressable, ScrollView, View } from "react-native";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import type { LocalDate } from "@/lib/date";
import { withDirectionParticle, withObjectParticle } from "@/lib/korean";
import { cn } from "@/lib/utils";

export type MoveStopTarget = {
  stopId: number;
  name: string;
  fromDate: LocalDate;
  /** 지금 자리. `지금 1일차 15:25 – 16:25`. */
  detail: string;
  /** 서버가 권한 날(넣지 못한 장소). 없으면 `null`. */
  suggestedDate: LocalDate | null;
};

export type MoveDayOption = { date: LocalDate; label: string; stopCount: number };

type MoveStopSheetProps = {
  /** 옮길 장소. 닫혀 있으면 `null`. */
  target: MoveStopTarget | null;
  days: readonly MoveDayOption[];
  onMove: (target: MoveStopTarget, toDate: LocalDate) => void;
  onRemove: (target: MoveStopTarget) => void;
  onClose: () => void;
};

/** 다른 일차로 옮기기(Figma W14-M). 옮기거나 빼면 화면이 바로 다시 계산한다. */
export function MoveStopSheet({ target, days, onMove, onRemove, onClose }: MoveStopSheetProps) {
  return (
    <BottomSheet visible={target !== null} onClose={onClose} closeLabel="일정 옮기기 닫기">
      {target === null ? null : <MoveStopContent target={target} days={days} onMove={onMove} onRemove={onRemove} />}
    </BottomSheet>
  );
}

/** 처음 고를 날. 서버가 권한 날, 다음 날, 다른 아무 날 순서다. 다른 날이 없으면 `null`. */
function getInitialTargetDate(target: MoveStopTarget, days: readonly MoveDayOption[]): LocalDate | null {
  const others = days.filter((day) => {
    return day.date !== target.fromDate;
  });

  const suggested = others.find((day) => {
    return day.date === target.suggestedDate;
  });

  const fromIndex = days.findIndex((day) => {
    return day.date === target.fromDate;
  });

  const nextDay = days.at(fromIndex + 1);
  return (suggested ?? nextDay ?? others.at(0))?.date ?? null;
}

function MoveStopContent({ target, days, onMove, onRemove }: Omit<MoveStopSheetProps, "target" | "onClose"> & { target: MoveStopTarget }) {
  const [toDate, setToDate] = useState(() => {
    return getInitialTargetDate(target, days);
  });

  const labelOf = (date: LocalDate | null) => {
    return days.find((day) => {
      return day.date === date;
    })?.label;
  };

  const fromLabel = labelOf(target.fromDate);
  const toLabel = labelOf(toDate);

  return (
    <View className="gap-4 px-5 pb-4 pt-2" testID="schedule-draft.move-sheet">
      <View className="gap-1">
        <Text accessibilityRole="header" className="text-h3 text-foreground">{`${withObjectParticle(target.name)} 어느 날로 옮길까요?`}</Text>
        <Text className="text-caption text-foreground-secondary tabular-nums">{target.detail}</Text>
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerClassName="gap-2">
        {days.map((day) => {
          const current = day.date === target.fromDate;
          const selected = day.date === toDate;

          return (
            <Pressable
              key={day.date}
              accessibilityRole="button"
              accessibilityLabel={current ? `${day.label}, 지금 있는 날` : `${day.label}, ${day.stopCount}곳에서 ${day.stopCount + 1}곳으로`}
              accessibilityState={{ selected, disabled: current }}
              disabled={current}
              className={cn("h-14 w-20 items-center justify-center rounded-md border", selected ? "border-primary bg-primary" : "border-border bg-card active:bg-muted")}

              onPress={() => {
                setToDate(day.date);
              }}

              testID={`schedule-draft.move-sheet.${day.date}`}
            >
              <Text className={cn("text-label", selected ? "text-primary-foreground" : current ? "text-muted-foreground" : "text-foreground")}>{day.label}</Text>
              <Text className={cn("text-micro tabular-nums", selected ? "text-primary-foreground" : "text-muted-foreground")}>
                {current ? "지금 여기" : `${day.stopCount}곳 → ${day.stopCount + 1}곳`}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>
      <View className="flex-row gap-2 rounded-md bg-muted p-3">
        <Icon as={Info} size={16} className="mt-0.5 text-icon" />
        <Text className="flex-1 text-caption text-foreground-secondary">
          {toLabel === undefined || fromLabel === undefined ? "옮길 수 있는 다른 날이 없어요" : `옮기면 ${fromLabel}와 ${toLabel}를 다시 계산해요`}
        </Text>
      </View>
      <Button
        size="lg"
        className="w-full"
        disabled={toDate === null}

        onPress={() => {
          if (toDate !== null) {
            onMove(target, toDate);
          }
        }}

        testID="schedule-draft.move-sheet.move"
      >
        <Text>{toLabel === undefined ? "옮기기" : `${withDirectionParticle(toLabel)} 옮기기`}</Text>
      </Button>
      <Pressable
        accessibilityRole="button"
        className="h-touch items-center justify-center rounded-sm active:bg-muted"

        onPress={() => {
          onRemove(target);
        }}

        testID="schedule-draft.move-sheet.remove"
      >
        <Text className="text-label text-foreground-secondary">일정에서 빼고 후보 장소로 돌려보내기</Text>
      </Pressable>
    </View>
  );
}
