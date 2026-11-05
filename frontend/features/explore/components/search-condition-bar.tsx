import { Fragment } from "react";
import { Pressable, View } from "react-native";
import { CalendarDays, Car, MapPin, type LucideIcon } from "lucide-react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useRegions } from "@/features/common/api/queries";
import { cn } from "@/lib/utils";
import { filterLabels, type ConditionGroup, type CourseFilters } from "../model";

const segments: { group: ConditionGroup; title: string; icon: LucideIcon }[] = [
  { group: "region", title: "지역", icon: MapPin },
  { group: "duration", title: "기간", icon: CalendarDays },
  { group: "transport", title: "이동 수단", icon: Car },
];

type SearchConditionBarProps = {
  filters: CourseFilters;
  onPress: () => void;
};

// 지역 | 기간 | 이동 수단을 한 줄로 보여 주고, 칸을 누르면 조건 시트를 연다.
export function SearchConditionBar({ filters, onPress }: SearchConditionBarProps) {
  const regions = useRegions();
  const labels = filterLabels(filters, regions.data ?? []);

  return (
    <View className="mx-5 flex-row items-center rounded-md border border-border bg-card" testID="course-search.conditions">
      {segments.map(({ group, title, icon }, index) => {
        const active = filters[group] !== "all";

        return (
          <Fragment key={group}>
            {index > 0 && <View className="h-7 w-px bg-border" />}
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`${title} ${labels[group]}`}
              accessibilityHint="검색 조건을 바꿔요"
              onPress={onPress}
              className="min-h-touch flex-1 flex-row items-center gap-2 py-2 pl-3 pr-2 active:bg-muted"
              testID={`course-search.condition.${group}`}
            >
              <Icon as={icon} size={18} className={active ? "text-primary" : "text-icon"} />
              <View className="min-w-0 flex-1">
                <Text className="text-caption text-muted-foreground">{title}</Text>
                <Text numberOfLines={1} className={cn("text-label", active ? "text-primary" : "text-foreground")}>{labels[group]}</Text>
              </View>
            </Pressable>
          </Fragment>
        );
      })}
    </View>
  );
}
