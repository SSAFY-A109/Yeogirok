import { useState } from "react";
import { Pressable, SectionList, View } from "react-native";
import { router } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Icon } from "@/components/ui/icon";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { cn } from "@/lib/utils";
import { useCourse } from "../api/queries";
import { groupPlacesByDay, type CourseDetail, type CoursePlace } from "../model";
import { placeCategories } from "./category-icons";
import { PhotoPlaceholder, toneAt } from "./photo-placeholder";
import { InlineMessage } from "./query-states";
import { RoomSelectSheet } from "./room-select-sheet";
import { CourseRouteMap } from "./course-route-map";

const AVATAR_SIZE = 26;
const AVATAR_OVERLAP = 6;
const avatarToneClassNames = ["bg-brand-muted", "bg-highlight-muted", "bg-accent"];

export function CourseDetailScreen({ courseId }: { courseId: string }) {
  const course = useCourse(courseId);

  return (
    <SafeAreaView className="flex-1 bg-background" testID="course-detail.screen">
      <ScreenHeader title="코스 상세" onBack={() => {
        return router.back();
      }} />
      {course.isPending && <DetailSkeleton />}
      {course.isError && (
        <View className="px-5 pt-4">
          <InlineMessage title="코스를 불러오지 못했어요" description="연결을 확인하고 다시 시도해 주세요." actionLabel="다시 시도" onAction={() => {
            return void course.refetch();
          }} testID="course-detail.error" />
        </View>
      )}
      {course.data === null && (
        <View className="px-5 pt-4">
          <InlineMessage title="코스를 찾을 수 없어요" description="삭제되었거나 주소가 잘못된 코스예요." actionLabel="돌아가기" onAction={() => {
            return router.back();
          }} testID="course-detail.not-found" />
        </View>
      )}
      {course.data && <PlacePicker course={course.data} />}
    </SafeAreaView>
  );
}

function PlacePicker({ course }: { course: CourseDetail }) {
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [sheetOpen, setSheetOpen] = useState(false);
  const [notice, setNotice] = useState("");

  const orderById = new Map(course.places.map((place, index) => {
    return [place.id, index + 1];
  }));

  const sections = groupPlacesByDay(course.places).map((group) => {
    return { day: group.day, data: group.places };
  });

  const selectedPlaces = course.places.filter((place) => {
    return selected.has(place.id);
  });

  const selectedIds = selectedPlaces.map((place) => {
    return place.id;
  });

  const selectedNames = selectedPlaces.map((place) => {
    return place.name;
  });

  const allSelected = selected.size === course.places.length;

  function toggle(placeId: string) {
    const next = new Set(selected);

    if (!next.delete(placeId)) {
      next.add(placeId);
    }

    setSelected(next);
  }

  function toggleAll() {
    setSelected(allSelected ? new Set() : new Set(course.places.map((place) => {
      return place.id;
    })));
  }

  return (
    <>
      <CourseRouteMap places={course.places} selectedIds={selected} onPressPlace={toggle} />
      <SectionList
        className="flex-1"
        sections={sections}
        keyExtractor={(place) => {
          return place.id;
        }}
        stickySectionHeadersEnabled={false}
        ListHeaderComponent={
          <View>
            <CourseInfo course={course} />
            <View className="flex-row items-center justify-between px-5 pb-2 pt-4">
              <Text accessibilityRole="header" className="text-h3 text-foreground">가져올 장소를 골라 주세요</Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={allSelected ? "전체 선택 해제" : "전체 선택"}
                hitSlop={12}
                onPress={toggleAll}
                testID="course-detail.select-all"
              >
                <Text className="text-label text-primary">{allSelected ? "전체 해제" : "전체 선택"}</Text>
              </Pressable>
            </View>
          </View>
        }
        renderSectionHeader={({ section }) => {
          return (
            <Text accessibilityRole="header" className="px-5 pb-1 pt-4 text-label-sm text-muted-foreground">{`${section.day}일차`}</Text>
          );
        }}
        renderItem={({ item }) => {
          const order = orderById.get(item.id) ?? 0;

          return <PlaceRow place={item} order={order} selected={selected.has(item.id)} onToggle={() => {
            return toggle(item.id);
          }} />;
        }}
        ListFooterComponent={<View className="h-4" />}
      />
      {notice !== "" && (
        <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="bg-brand-muted px-5 py-3 text-label text-primary" testID="course-detail.notice">{notice}</Text>
      )}
      <SelectionBar count={selected.size} onAdd={() => {
        setNotice("");
        setSheetOpen(true);
      }} />
      <RoomSelectSheet
        visible={sheetOpen}
        placeIds={selectedIds}
        placeNames={selectedNames}
        onClose={() => {
          setSheetOpen(false);
        }}
        onAdded={(result) => {
          setSheetOpen(false);
          setSelected(new Set());
          setNotice(`${result.roomName}에 ${result.addedCount}곳을 담았어요`);
        }}
      />
    </>
  );
}

function CourseInfo({ course }: { course: CourseDetail }) {
  return (
    <View className="gap-2 px-5 pb-4 pt-5">
      <Text accessibilityRole="header" className="text-h2 text-foreground">{course.title}</Text>
      <View className="flex-row items-center gap-2.5">
        <View className="flex-row" accessibilityLabel={`다녀온 사람 ${course.travelers.length}명`}>
          {course.travelers.map((name, index) => {
            return (
              <View
                key={name}
                className={cn("items-center justify-center rounded-full border-2 border-card", avatarToneClassNames[index % avatarToneClassNames.length])}
                style={{ width: AVATAR_SIZE, height: AVATAR_SIZE, marginRight: index < course.travelers.length - 1 ? -AVATAR_OVERLAP : 0, zIndex: course.travelers.length - index }}
              >
                <Text className="text-micro text-foreground">{name}</Text>
              </View>
            );
          })}
        </View>
        <Text className="text-caption text-foreground-secondary">{`${course.duration} ${course.transport} 장소 ${course.placeCount}곳`}</Text>
      </View>
      <Text className="text-body-sm text-foreground-secondary">{course.description}</Text>
    </View>
  );
}

type PlaceRowProps = { place: CoursePlace; order: number; selected: boolean; onToggle: () => void };

function PlaceRow({ place, order, selected, onToggle }: PlaceRowProps) {
  const category = placeCategories[place.category];

  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={`${order}번 ${place.name}, ${placeCategories[place.category].label}`}
      accessibilityState={{ checked: selected }}
      onPress={onToggle}
      className="flex-row items-center gap-3 px-5 py-3 active:bg-muted"
      testID={`course-detail.place.${place.id}`}
    >
      <Checkbox checked={selected} onCheckedChange={onToggle} accessible={false} />
      <View className={cn("items-center justify-center rounded-full", selected ? "bg-primary" : "bg-muted")} style={{ width: 22, height: 22 }}>
        <Text className={cn("text-micro tabular-nums", selected ? "text-primary-foreground" : "text-foreground-secondary")}>{order}</Text>
      </View>
      <View className="flex-1 gap-1">
        <Text numberOfLines={1} className="text-label text-foreground">{place.name}</Text>
        <View className="flex-row items-center gap-1">
          <Icon as={category.icon} size={14} className="text-icon" />
          <Text numberOfLines={1} className="shrink-0 text-caption text-foreground-secondary">{category.label}</Text>
        </View>
      </View>
      <PhotoPlaceholder size={52} tone={toneAt(order)} className="rounded-sm" />
    </Pressable>
  );
}

function SelectionBar({ count, onAdd }: { count: number; onAdd: () => void }) {
  return (
    <View className="flex-row items-center gap-4 border-t border-border bg-card px-5 pb-2 pt-3" testID="course-detail.selection-bar">
      <View className="flex-1">
        <Text accessibilityLiveRegion="polite" className="text-h3 text-foreground tabular-nums">{`${count}곳 선택`}</Text>
        <Text className="text-caption text-muted-foreground">여러 코스에서 모아도 돼요</Text>
      </View>
      <Button size="lg" className="w-44" disabled={count === 0} onPress={onAdd} accessibilityLabel={`선택한 ${count}곳을 여행방에 담기`} testID="course-detail.add">
        <Text>여행방에 담기</Text>
      </Button>
    </View>
  );
}

function DetailSkeleton() {
  return (
    <View accessibilityLabel="불러오는 중" className="gap-4" testID="course-detail.loading">
      <Skeleton className="w-full rounded-none" style={{ height: 196 }} />
      <View className="gap-3 px-5">
        <Skeleton className="h-7 w-skeleton-heading" />
        <Skeleton className="h-4 w-skeleton-line" />
        <Skeleton className="h-16 w-full" />
      </View>
    </View>
  );
}
