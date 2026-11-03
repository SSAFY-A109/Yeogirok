import { useState } from "react";
import { FlatList, Pressable, ScrollView, View } from "react-native";
import { router } from "expo-router";
import { ChevronDown, ChevronLeft, Clock, Footprints, X } from "lucide-react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { FilterChip } from "@/components/ui/filter-chip";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { useCourseSearch } from "../api/queries";
import { defaultCourseFilters, describeFilters, isDefaultFilters, type CourseFilters, type CourseSummary } from "../model";
import { addRecentSearch, clearRecentSearches, removeRecentSearch, useRecentSearches, type RecentSearch } from "../recent-searches";
import { PhotoPlaceholder, toneAt } from "./photo-placeholder";
import { InlineMessage, ListSkeleton } from "./query-states";
import { SearchConditionBar } from "./search-condition-bar";
import { SearchConditionSheet } from "./search-condition-sheet";

// 서버 인기 지역 API가 정해지기 전 시안(W03-A) 문구 그대로 둔다(TBD).
const popularKeywords = ["강릉", "제주", "부산", "경주", "여수", "전주", "속초"];

type CourseSearchScreenProps = { query: string; filters: CourseFilters; searched: boolean };

export function CourseSearchScreen({ query, filters, searched }: CourseSearchScreenProps) {
  const [draft, setDraft] = useState(query);
  const [sheetOpen, setSheetOpen] = useState(false);

  function search(nextQuery: string, nextFilters: CourseFilters) {
    const trimmed = nextQuery.trim();

    setDraft(trimmed);
    addRecentSearch(trimmed, nextFilters);
    router.setParams({ query: trimmed, ...nextFilters, searched: "1" });
  }

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
            search(draft, filters);
          }}
          autoFocus={!searched}
          placeholder="지역, 장소, 코스 검색"
          returnKeyType="search"
          testID="course-search.input"
        />
      </View>
      <View className="pb-3 pt-1">
        <SearchConditionBar filters={filters} onPress={() => {
          setSheetOpen(true);
        }} />
      </View>
      {searched ? (
        <CourseResults query={query} filters={filters} onResetFilters={() => {
          search(query, defaultCourseFilters);
        }} />
      ) : (
        <SearchEntry
          onSelectRecent={(item) => {
            search(item.query, item.filters);
          }}
          onSelectKeyword={(keyword) => {
            search(keyword, filters);
          }}
        />
      )}
      <SearchConditionSheet
        visible={sheetOpen}
        query={searched ? query : draft.trim()}
        filters={filters}
        onClose={() => {
          setSheetOpen(false);
        }}
        onApply={(nextFilters) => {
          setSheetOpen(false);
          search(draft, nextFilters);
        }}
      />
    </SafeAreaView>
  );
}

function SearchEntry({ onSelectRecent, onSelectKeyword }: { onSelectRecent: (item: RecentSearch) => void; onSelectKeyword: (keyword: string) => void }) {
  const recentSearches = useRecentSearches();

  return (
    <ScrollView className="flex-1 border-t-8 border-muted" contentContainerClassName="gap-8 px-5 pb-8 pt-6" keyboardShouldPersistTaps="handled" testID="course-search.entry">
      <View className="gap-2">
        <View className="flex-row items-center justify-between">
          <Text accessibilityRole="header" className="text-h3 text-foreground">최근 검색</Text>
          {recentSearches.length > 0 && (
            <Pressable accessibilityRole="button" accessibilityLabel="최근 검색 전체 삭제" hitSlop={8} onPress={clearRecentSearches} className="min-h-touch justify-center" testID="course-search.recent.clear">
              <Text className="text-body-sm text-foreground-secondary underline">전체 삭제</Text>
            </Pressable>
          )}
        </View>
        {recentSearches.length === 0 && <Text className="py-2 text-body-sm text-muted-foreground" testID="course-search.recent.empty">최근 검색이 없어요</Text>}
        {recentSearches.map((item) => {
          return <RecentRow key={item.id} item={item} onPress={onSelectRecent} />;
        })}
      </View>
      <View className="gap-3">
        <Text accessibilityRole="header" className="text-h3 text-foreground">많이 찾는 지역</Text>
        <View className="flex-row flex-wrap gap-2">
          {popularKeywords.map((keyword) => {
            return (
              <FilterChip key={keyword} onPress={() => {
                onSelectKeyword(keyword);
              }} testID={`course-search.popular.${keyword}`}>
                {keyword}
              </FilterChip>
            );
          })}
        </View>
      </View>
    </ScrollView>
  );
}

function RecentRow({ item, onPress }: { item: RecentSearch; onPress: (item: RecentSearch) => void }) {
  const title = item.query === "" ? "전체 코스" : item.query;
  const conditions = describeFilters(item.filters);

  return (
    <View className="flex-row items-center gap-3">
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={conditions ? `${title}, ${conditions}로 다시 검색` : `${title} 다시 검색`}
        onPress={() => {
          onPress(item);
        }}
        className="min-h-touch flex-1 flex-row items-center gap-3 py-2 active:bg-muted"
      >
        <View className="size-9 items-center justify-center rounded-full bg-muted">
          <Icon as={Clock} size={18} className="text-icon" />
        </View>
        <View className="flex-1">
          <Text numberOfLines={1} className="text-body text-foreground">{title}</Text>
          {conditions !== "" && <Text numberOfLines={1} className="text-caption text-muted-foreground">{conditions}</Text>}
        </View>
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${title} 최근 검색 삭제`}
        onPress={() => {
          removeRecentSearch(item.id);
        }}
        className="size-11 items-center justify-center"
      >
        <Icon as={X} size={18} className="text-icon" />
      </Pressable>
    </View>
  );
}

function CourseResults({ query, filters, onResetFilters }: { query: string; filters: CourseFilters; onResetFilters: () => void }) {
  const search = useCourseSearch(query, filters);
  const filtered = !isDefaultFilters(filters);

  return (
    <View className="flex-1">
      <View className="flex-row items-center justify-between px-5 pb-2 pt-2">
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
          ListEmptyComponent={filtered ? (
            <InlineMessage title="조건에 맞는 코스가 없어요" description="지역·기간·이동 수단 조건을 줄여 보세요." actionLabel="조건 초기화" onAction={onResetFilters} testID="course-search.empty" />
          ) : (
            <InlineMessage title="검색 결과가 없어요" description="다른 지역이나 장소 이름으로 찾아보세요." testID="course-search.empty" />
          )}
        />
      )}
    </View>
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
