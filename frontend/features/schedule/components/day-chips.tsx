import { CalendarDays } from "lucide-react-native";
import { useEffect, useRef } from "react";
import { Pressable, ScrollView, View, type ScrollViewInstance } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { DAY_PICKER_MIN_DAYS } from "@/features/schedule/model";
import type { LocalDate } from "@/lib/date";
import { cn } from "@/lib/utils";

/** 칩을 자동으로 넘길 때 왼쪽에 남기는 여백. 앞 칩이 조금 보여 넘길 수 있음을 알린다. */
const SCROLL_LEAD = 20;

export type DayChipItem = {
  date: LocalDate;
  /** `1일차`. */
  label: string;
  /** 일정 탭은 날짜(`11/8 일`), 초안 조정은 장소 수(`5곳`). */
  caption: string;
  /** 넣지 못한 장소가 있는 날(코랄 점). */
  hasWarning: boolean;
};

type DayChipsProps = {
  items: readonly DayChipItem[];
  selectedDate: LocalDate;
  onSelect: (date: LocalDate) => void;
  /** 긴 여행(5일 이상)에서 일차 선택 시트(W14-L2)를 연다. */
  onOpenPicker: () => void;
  testID: string;
};

/**
 * 일차 칩 줄(Figma W14·W09-B, 긴 여행은 W14-L). 칩 너비는 고정이고 가로로 넘긴다.
 * 고른 칩은 늘 보이게 자동으로 넘기고, 5일 이상이면 오른쪽에 일차 선택 버튼을 고정한다.
 */
export function DayChips({ items, selectedDate, onSelect, onOpenPicker, testID }: DayChipsProps) {
  const scrollRef = useRef<ScrollViewInstance>(null);
  const offsetsRef = useRef(new Map<LocalDate, number>());
  const showPicker = items.length >= DAY_PICKER_MIN_DAYS;

  // 고른 날이 바뀌면 그 칩이 보이도록 가로 스크롤을 맞춘다(스크롤 뷰와 동기화).
  useEffect(() => {
    const offset = offsetsRef.current.get(selectedDate);

    if (offset !== undefined) {
      scrollRef.current?.scrollTo({ x: Math.max(offset - SCROLL_LEAD, 0), animated: true });
    }
  }, [selectedDate]);

  return (
    <View className="flex-row items-center pb-3" testID={testID}>
      <ScrollView ref={scrollRef} horizontal showsHorizontalScrollIndicator={false} className="flex-1" contentContainerClassName="gap-2 px-5">
        {items.map((item) => {
          const selected = item.date === selectedDate;

          return (
            <Pressable
              key={item.date}
              accessibilityRole="button"
              accessibilityLabel={`${item.label} ${item.caption}${item.hasWarning ? ", 넣지 못한 장소 있음" : ""}`}
              accessibilityState={{ selected }}
              className={cn("h-14 w-16 items-center justify-center rounded-md border", selected ? "border-primary bg-primary" : "border-border bg-card active:bg-muted")}

              onLayout={(event) => {
                offsetsRef.current.set(item.date, event.nativeEvent.layout.x);
              }}

              onPress={() => {
                onSelect(item.date);
              }}

              testID={`${testID}.${item.date}`}
            >
              <Text className={cn("text-label", selected ? "text-primary-foreground" : "text-foreground")}>{item.label}</Text>
              <Text className={cn("text-micro", selected ? "text-primary-foreground" : "text-foreground-secondary")}>{item.caption}</Text>
              {item.hasWarning ? <View accessible={false} className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-highlight" /> : null}
            </Pressable>
          );
        })}
      </ScrollView>
      {showPicker ? (
        <View className="pl-2 pr-5">
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="일차 선택"
            className="h-14 w-11 items-center justify-center rounded-md border border-border bg-card active:bg-muted"
            onPress={onOpenPicker}
            testID={`${testID}.picker`}
          >
            <Icon as={CalendarDays} size={20} className="text-icon" />
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}
