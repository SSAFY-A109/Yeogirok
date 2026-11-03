import { useState, type ReactNode } from "react";
import { Pressable, View } from "react-native";
import { Bus, Car, type LucideIcon } from "lucide-react-native";

import { BottomSheet } from "@/components/ui/bottom-sheet";
import { Button } from "@/components/ui/button";
import { FilterChip } from "@/components/ui/filter-chip";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { cn } from "@/lib/utils";
import { useCourseSearch } from "../api/queries";
import { defaultCourseFilters, durationFilterOptions, regionFilterOptions, transportFilterOptions, type CourseFilters, type TransportFilter } from "../model";

const transportIcons: Record<Exclude<TransportFilter, "all">, LucideIcon> = { car: Car, public: Bus };

type SearchConditionSheetProps = {
  visible: boolean;
  query: string;
  filters: CourseFilters;
  onClose: () => void;
  onApply: (filters: CourseFilters) => void;
};

export function SearchConditionSheet({ visible, onClose, ...props }: SearchConditionSheetProps) {
  return (
    <BottomSheet visible={visible} onClose={onClose} closeLabel="검색 조건 닫기">
      <ConditionForm {...props} />
    </BottomSheet>
  );
}

function ConditionForm({ query, filters, onApply }: Omit<SearchConditionSheetProps, "visible" | "onClose">) {
  const [draft, setDraft] = useState(filters);
  const preview = useCourseSearch(query, draft);

  function update(next: Partial<CourseFilters>) {
    setDraft({ ...draft, ...next });
  }

  return (
    <View className="gap-6 px-5 pb-2 pt-2" testID="course-search.sheet">
      <Text accessibilityRole="header" className="text-h3 text-foreground">검색 조건</Text>
      <ConditionGroup title="지역" description="코스 장소가 있는 지역">
        {regionFilterOptions.map((option) => {
          return (
            <FilterChip key={option.value} selected={draft.region === option.value} onSelectedChange={() => {
              update({ region: option.value });
            }} testID={`course-search.sheet.region.${option.value}`}>
              {option.label}
            </FilterChip>
          );
        })}
      </ConditionGroup>
      <ConditionGroup title="기간" description="코스 일정 길이">
        {durationFilterOptions.map((option) => {
          return (
            <FilterChip key={option.value} selected={draft.duration === option.value} onSelectedChange={() => {
              update({ duration: option.value });
            }} testID={`course-search.sheet.duration.${option.value}`}>
              {option.label}
            </FilterChip>
          );
        })}
      </ConditionGroup>
      <ConditionGroup title="이동 수단" description="선택하지 않으면 전체">
        {transportFilterOptions.map((option) => {
          const selected = draft.transport === option.value;

          return (
            <Pressable
              key={option.value}
              accessibilityRole="button"
              accessibilityLabel={option.label}
              accessibilityState={{ selected }}
              onPress={() => {
                update({ transport: selected ? "all" : option.value });
              }}
              className={cn("min-h-touch flex-1 flex-row items-center justify-center gap-2 rounded-md border bg-card py-3", selected ? "border-ring bg-chip-selected" : "border-border")}
              testID={`course-search.sheet.transport.${option.value}`}
            >
              <Icon as={transportIcons[option.value]} size={20} className={selected ? "text-primary" : "text-icon"} />
              <Text className={cn("text-label", selected ? "text-primary" : "text-foreground")}>{option.label}</Text>
            </Pressable>
          );
        })}
      </ConditionGroup>
      <View className="flex-row gap-2 pt-2">
        <Button variant="outline" size="lg" className="h-button-lg" onPress={() => {
          setDraft(defaultCourseFilters);
        }} testID="course-search.sheet.reset">
          <Text>초기화</Text>
        </Button>
        <Button size="lg" className="h-button-lg flex-1" onPress={() => {
          onApply(draft);
        }} testID="course-search.sheet.apply">
          <Text className="tabular-nums">{preview.data ? `코스 ${preview.data.length}개 보기` : "코스 보기"}</Text>
        </Button>
      </View>
    </View>
  );
}

function ConditionGroup({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <View className="gap-3">
      <View className="flex-row items-baseline gap-2">
        <Text className="text-label text-foreground">{title}</Text>
        <Text className="text-caption text-muted-foreground">{description}</Text>
      </View>
      <View className="flex-row flex-wrap gap-2">{children}</View>
    </View>
  );
}
