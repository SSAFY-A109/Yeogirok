import { Pressable, View } from "react-native";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Text } from "@/components/ui/text";
import type { DayChipItem } from "@/features/schedule/components/day-chips";
import { formatDateRange, formatTripLength, type LocalDate } from "@/lib/date";
import { cn } from "@/lib/utils";

const COLUMNS = 4;

type DayPickerSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  items: readonly DayChipItem[];
  selectedDate: LocalDate;
  onSelect: (date: LocalDate) => void;
  testID: string;
};

function toRows(items: readonly DayChipItem[]): (DayChipItem | null)[][] {
  const rows: (DayChipItem | null)[][] = [];

  for (let index = 0; index < items.length; index += COLUMNS) {
    const row: (DayChipItem | null)[] = items.slice(index, index + COLUMNS);

    while (row.length < COLUMNS) {
      row.push(null);
    }

    rows.push(row);
  }

  return rows;
}

/** 일차 선택 시트(Figma W14-L2). 전체 일차를 4열로 보여 주고, 누르면 그 날로 옮긴 뒤 닫힌다. */
export function DayPickerSheet({ open, onOpenChange, items, selectedDate, onSelect, testID }: DayPickerSheetProps) {
  const first = items.at(0);
  const last = items.at(-1);

  const hasWarning = items.some((item) => {
    return item.hasWarning;
  });

  const close = () => {
    onOpenChange(false);
  };

  return (
    <BottomSheet visible={open} onClose={close} closeLabel="일차 선택 닫기">
      <View className="gap-4 px-5 pb-4 pt-2" testID={testID}>
        <View className="gap-1">
          <Text accessibilityRole="header" className="text-h3 text-foreground">일차 선택</Text>
          {first !== undefined && last !== undefined ? (
            <Text className="text-caption text-foreground-secondary">
              {`${formatDateRange(first.date, last.date, { weekday: false })} ${formatTripLength(first.date, last.date)}`}
            </Text>
          ) : null}
        </View>
        <View className="gap-2">
          {toRows(items).map((row, rowIndex) => {
            return (
              <View key={row[0]?.date ?? rowIndex} className="flex-row gap-2">
                {row.map((item, columnIndex) => {
                  if (item === null) {
                    return <View key={`empty-${columnIndex}`} className="flex-1" />;
                  }

                  const selected = item.date === selectedDate;

                  return (
                    <Pressable
                      key={item.date}
                      accessibilityRole="button"
                      accessibilityLabel={`${item.label} ${item.caption}${item.hasWarning ? ", 확인할 내용 있음" : ""}`}
                      accessibilityState={{ selected }}
                      className={cn("h-14 flex-1 items-center justify-center rounded-md border", selected ? "border-primary bg-primary" : "border-border bg-card active:bg-muted")}

                      onPress={() => {
                        onSelect(item.date);
                        close();
                      }}

                      testID={`${testID}.${item.date}`}
                    >
                      <Text className={cn("text-label", selected ? "text-primary-foreground" : "text-foreground")}>{item.label}</Text>
                      <Text className={cn("text-micro", selected ? "text-primary-foreground" : "text-foreground-secondary")}>{item.caption}</Text>
                      {item.hasWarning ? <View accessible={false} className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-highlight" /> : null}
                    </Pressable>
                  );
                })}
              </View>
            );
          })}
        </View>
        {hasWarning ? (
          <View className="flex-row items-center gap-2">
            <View accessible={false} className="h-1.5 w-1.5 rounded-full bg-highlight" />
            <Text className="text-caption text-foreground-secondary">확인할 내용이 있는 날</Text>
          </View>
        ) : null}
      </View>
    </BottomSheet>
  );
}
