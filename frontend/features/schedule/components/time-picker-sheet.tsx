import { Pressable, View } from "react-native";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Text } from "@/components/ui/text";
import { buildTimeOptions, type LocalTime } from "@/features/schedule/model";
import { cn } from "@/lib/utils";

/** 고를 수 있는 시각(30분 간격). 범위는 시안이 정하지 않아 정한 값이다(TBD). */
const TIME_OPTIONS = buildTimeOptions("06:00", "23:30", 30);
const COLUMNS = 4;

type TimePickerSheetProps = {
  /** 열려 있으면 고치는 시각의 이름(`1일차 시작`). 닫혀 있으면 `null`. */
  title: string | null;
  value: LocalTime;
  onSelect: (time: LocalTime) => void;
  onClose: () => void;
};

function toRows(options: readonly LocalTime[]): (LocalTime | null)[][] {
  const rows: (LocalTime | null)[][] = [];

  for (let index = 0; index < options.length; index += COLUMNS) {
    const row: (LocalTime | null)[] = options.slice(index, index + COLUMNS);

    while (row.length < COLUMNS) {
      row.push(null);
    }

    rows.push(row);
  }

  return rows;
}

/** W13 시각 선택("시각을 누르면 시간 선택기가 열려요"). 30분 간격 칸을 누르면 바로 정해지고 닫힌다. */
export function TimePickerSheet({ title, value, onSelect, onClose }: TimePickerSheetProps) {
  return (
    <BottomSheet visible={title !== null} onClose={onClose} closeLabel="시각 선택 닫기">
      <View className="gap-4 px-5 pb-4 pt-2" testID="schedule-conditions.time-sheet">
        <Text accessibilityRole="header" className="text-h3 text-foreground">{`${title ?? ""} 시각`}</Text>
        <View className="gap-2">
          {toRows(TIME_OPTIONS).map((row) => {
            return (
              <View key={row[0] ?? "empty"} className="flex-row gap-2">
                {row.map((time, index) => {
                  if (time === null) {
                    return <View key={`empty-${index}`} className="flex-1" />;
                  }

                  const selected = time === value;

                  return (
                    <Pressable
                      key={time}
                      accessibilityRole="button"
                      accessibilityLabel={time}
                      accessibilityState={{ selected }}
                      className={cn("h-11 flex-1 items-center justify-center rounded-sm border", selected ? "border-ring bg-chip-selected" : "border-border bg-card active:bg-muted")}

                      onPress={() => {
                        onSelect(time);
                        onClose();
                      }}

                      testID={`schedule-conditions.time-${time.replace(":", "")}`}
                    >
                      <Text className={cn("text-label tabular-nums", selected ? "text-primary-hover" : "text-foreground-secondary")}>{time}</Text>
                    </Pressable>
                  );
                })}
              </View>
            );
          })}
        </View>
      </View>
    </BottomSheet>
  );
}
