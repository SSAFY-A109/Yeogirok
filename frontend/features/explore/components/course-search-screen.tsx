import { useState } from "react";
import { FlatList, Pressable, ScrollView, View } from "react-native";
import { router } from "expo-router";
import { ChevronDown, ChevronLeft, Footprints } from "lucide-react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { FilterChip } from "@/components/ui/filter-chip";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { useCourseSearch } from "../api/queries";
import { defaultCourseFilters, durationFilterOptions, type CourseFilters, type CourseSummary } from "../model";
import { PhotoPlaceholder, toneAt } from "./photo-placeholder";
import { InlineMessage, ListSkeleton } from "./query-states";

export function CourseSearchScreen({ initialQuery }: { initialQuery: string }) {
  const [draft, setDraft] = useState(initialQuery);
  const [query, setQuery] = useState(initialQuery);
  const [filters, setFilters] = useState<CourseFilters>(defaultCourseFilters);
  const search = useCourseSearch(query, filters);

  return (
    <SafeAreaView className="flex-1 bg-background" testID="course-search.screen">
      <View className="flex-row items-center gap-1 py-2 pl-1 pr-5">
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="뒤로 가기"
          onPress={() => {
            return router.back();
          }}
          className="size-11 items-center justify-center"
          testID="course-search.back"
        >
          <Icon as={ChevronLeft} size={24} className="text-icon" />
        </Pressable>
        <TextField
          containerClassName="flex-1"
          value={draft}
          onChangeText={setDraft}
          onSubmitEditing={() => {
            return setQuery(draft.trim());
          }}
          placeholder="지역, 장소, 코스 검색"
          returnKeyType="search"
          testID="course-search.input"
        />
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerClassName="gap-2 px-5 py-2" className="grow-0">
        {durationFilterOptions.map((option) => {
          return (
            <FilterChip
              key={option.value}
              selected={filters.duration === option.value}
              onSelectedChange={() => {
                return setFilters({ ...filters, duration: option.value });
              }}
              testID={`course-search.filter.${option.value}`}
            >
              {option.label}
            </FilterChip>
          );
        })}
        <FilterChip selected={filters.byCar} onSelectedChange={(byCar) => {
          return setFilters({ ...filters, byCar });
        }} testID="course-search.filter.car">
          자차
        </FilterChip>
      </ScrollView>
      <View className="flex-row items-center justify-between px-5 pb-2 pt-4">
        <View className="flex-row items-center gap-1">
          <Text accessibilityRole="header" className="text-label text-foreground">{query ? `'${query}' 코스` : "전체 코스"}</Text>
          {search.data && <Text className="text-label text-primary tabular-nums">{`${search.data.length}개`}</Text>}
        </View>
        <View className="flex-row items-center gap-1" accessibilityLabel="정렬 추천순">
          <Text className="text-label text-foreground-secondary">추천순</Text>
          <Icon as={ChevronDown} size={16} className="text-icon" />
        </View>
      </View>
      {search.isPending && <View className="px-5"><ListSkeleton testID="course-search.loading" /></View>}
      {search.isError && (
        <View className="px-5">
          <InlineMessage title="코스를 불러오지 못했어요" description="연결을 확인하고 다시 시도해 주세요." actionLabel="다시 시도" onAction={() => {
            return void search.refetch();
          }} testID="course-search.error" />
        </View>
      )}
      {search.data && (
        <FlatList
          data={search.data}
          keyExtractor={(course) => {
            return course.id;
          }}
          keyboardShouldPersistTaps="handled"
          contentContainerClassName="px-5 pb-6"
          renderItem={({ item, index }) => {
            return <CourseRow course={item} last={index === search.data.length - 1} tone={toneAt(index)} />;
          }}
          ListEmptyComponent={<InlineMessage title="검색 결과가 없어요" description="다른 지역이나 장소 이름으로 찾아보세요." testID="course-search.empty" />}
        />
      )}
    </SafeAreaView>
  );
}

type CourseRowProps = { course: CourseSummary; last: boolean; tone: ReturnType<typeof toneAt> };

function CourseRow({ course, last, tone }: CourseRowProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${course.title}, 장소 ${course.placeCount}곳, ${course.duration}, ${course.transport}`}
      onPress={() => {
        return router.push({ pathname: "/courses/[courseId]", params: { courseId: course.id } });
      }}
      className={last ? "flex-row gap-4 py-4 active:bg-muted" : "flex-row gap-4 border-b border-border py-4 active:bg-muted"}
      testID={`course-search.row.${course.id}`}
    >
      <PhotoPlaceholder size={84} tone={tone} />
      <View className="flex-1 gap-1">
        <Text numberOfLines={1} className="text-label text-foreground">{course.title}</Text>
        <Text numberOfLines={1} className="text-caption text-foreground-secondary">{`장소 ${course.placeCount}곳 ${course.duration} ${course.transport}`}</Text>
        <Text numberOfLines={1} className="text-caption text-muted-foreground">{course.summary}</Text>
        <View className="flex-row items-center gap-1">
          <Icon as={Footprints} size={14} className="text-highlight" />
          <Text className="text-caption text-highlight tabular-nums">{`${course.forkCount}명이 장소를 가져갔어요`}</Text>
        </View>
      </View>
    </Pressable>
  );
}
