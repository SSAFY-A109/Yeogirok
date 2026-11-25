import { Pressable, View } from "react-native";

import { Text } from "@/components/ui/text";
import { cn } from "@/lib/utils";

export type PlaceTab = "places" | "schedule";

/** 36 높이 칸의 터치 영역을 44로 넓히는 여백(바깥 틀 안쪽 여백 4와 겹친다). */
const SEGMENT_HIT_SLOP = { top: 4, bottom: 4 };

type PlaceTabsProps = {
  value: PlaceTab;
  onValueChange: (tab: PlaceTab) => void;
  /** 화면에 보이는 후보 장소 수(빼기를 누른 장소 제외). */
  placeCount: number;
};

/** 여행방 상세의 "장소 N곳 / 일정" 세그먼트(Figma W09 `Segmented / 장소 일정`). */
export function PlaceTabs({ value, onValueChange, placeCount }: PlaceTabsProps) {
  const tabs: readonly { value: PlaceTab; label: string; testID: string }[] = [
    { value: "places", label: `장소 ${placeCount}곳`, testID: "room-detail.tab-places" },
    { value: "schedule", label: "일정", testID: "room-detail.tab-schedule" },
  ];

  return (
    <View className="px-5 pb-4">
      <View accessibilityRole="tablist" className="flex-row rounded-sm bg-muted p-1">
        {tabs.map((tab) => {
          const selected = tab.value === value;

          return (
            <Pressable
              key={tab.value}
              accessibilityRole="tab"
              accessibilityState={{ selected }}
              className={cn("h-9 flex-1 items-center justify-center rounded-xs", selected && "border border-border bg-card")}
              hitSlop={SEGMENT_HIT_SLOP}

              onPress={() => {
                onValueChange(tab.value);
              }}

              testID={tab.testID}
            >
              <Text className={selected ? "text-label text-foreground" : "text-body-sm text-foreground-secondary"}>{tab.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}
