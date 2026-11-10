import { useState } from "react";
import { Pressable, SectionList, View } from "react-native";
import { router } from "expo-router";
import { SafeAreaView } from "react-native-safe-area-context";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Icon } from "@/components/ui/icon";
import { MediaPhoto } from "@/components/ui/media-photo";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useCourse } from "@/features/course/api/queries";
import { getCourseErrorMessage, isCourseUnavailable } from "@/features/course/errors";
import { formatCourseFacts, type CourseDetail, type CoursePlace } from "@/features/course/model";
import { cn } from "@/lib/utils";
import { AuthorAvatars } from "./author-avatars";
import { categoryIconOf } from "./category-icons";
import { CourseRouteMap } from "./course-route-map";
import { InlineMessage } from "./query-states";
import { RoomSelectSheet } from "./room-select-sheet";

const PLACE_THUMBNAIL_SIZE = 52;

/** `courseId`가 `null`이면 주소의 코스 id가 숫자가 아닌 경우다. */
export function CourseDetailScreen({ courseId }: { courseId: number | null }) {
  const course = useCourse(courseId);
  const unavailable = courseId === null || isCourseUnavailable(course.error);

  return (
    <SafeAreaView className="flex-1 bg-background" testID="course-detail.screen">
      <ScreenHeader title="코스 상세" onBack={() => {
        return router.back();
      }} />
      {unavailable && (
        <View className="px-5 pt-4">
          <InlineMessage title="코스를 볼 수 없어요" description="공개가 해제되었거나 삭제된 코스예요." actionLabel="돌아가기" onAction={() => {
            return router.back();
          }} testID="course-detail.not-found" />
        </View>
      )}
      {!unavailable && course.isPending && <DetailSkeleton />}
      {!unavailable && course.isError && (
        <View className="px-5 pt-4">
          <InlineMessage title="코스를 불러오지 못했어요" description={getCourseErrorMessage(course.error)} actionLabel="다시 시도" onAction={() => {
            return void course.refetch();
          }} testID="course-detail.error" />
        </View>
      )}
      {course.isSuccess && <PlacePicker course={course.data} />}
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

  const sections = course.days.map((group) => {
    return { day: group.day, data: group.places };
  });

  const selectedPlaces = course.places.filter((place) => {
    return selected.has(place.id);
  });

  const selectedNames = selectedPlaces.map((place) => {
    return place.name;
  });

  const allSelected = course.places.length > 0 && selectedPlaces.length === course.places.length;

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
                accessibilityState={{ disabled: course.places.length === 0 }}
                disabled={course.places.length === 0}
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
            <View className="px-5 pb-1 pt-4">
              <Text accessibilityRole="header" className="text-label-sm text-muted-foreground">{`${section.day}일차`}</Text>
              {section.data.length === 0 && <Text className="pt-2 text-caption text-muted-foreground">공개된 장소가 없는 날이에요</Text>}
            </View>
          );
        }}
        renderItem={({ item }) => {
          return <PlaceRow place={item} order={orderById.get(item.id) ?? 0} selected={selected.has(item.id)} onToggle={() => {
            return toggle(item.id);
          }} />;
        }}
        ListFooterComponent={<View className="h-4" />}
      />
      {notice !== "" && (
        <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="bg-brand-muted px-5 py-3 text-label text-primary" testID="course-detail.notice">{notice}</Text>
      )}
      <SelectionBar count={selectedPlaces.length} onAdd={() => {
        setNotice("");
        setSheetOpen(true);
      }} />
      <RoomSelectSheet
        visible={sheetOpen}
        placeIds={selectedPlaces.map((place) => {
          return place.id;
        })}
        placeNames={selectedNames}
        onClose={() => {
          setSheetOpen(false);
        }}
        onAdded={(result) => {
          setSheetOpen(false);
          setSelected(new Set());
          setNotice(`${result.roomName}에 ${result.addedCount}곳을 담았어요`);
        }}
        onCreateRoom={() => {
          const visitIds = selectedPlaces.map((place) => {
            return place.visitId;
          });

          setSheetOpen(false);
          router.push({
            pathname: "/rooms/new",
            params: {
              recordId: String(course.id),
              visitIds: visitIds.join(","),
              placeNames: selectedNames.join(", "),
            },
          });
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
        <AuthorAvatars authors={course.authors} size={26} overlap={6} />
        <Text className="text-caption text-foreground-secondary">{formatCourseFacts({ ...course, placeCount: course.places.length })}</Text>
      </View>
      {course.description !== "" && <Text className="text-body-sm text-foreground-secondary">{course.description}</Text>}
    </View>
  );
}

type PlaceRowProps = { place: CoursePlace; order: number; selected: boolean; onToggle: () => void };

function PlaceRow({ place, order, selected, onToggle }: PlaceRowProps) {
  const categoryName = place.categoryName ?? "분류 없음";

  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={`${order}번 ${place.name}, ${categoryName}`}
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
          <Icon as={categoryIconOf(place.categoryCode)} size={14} className="text-icon" />
          <Text numberOfLines={1} className="shrink-0 text-caption text-foreground-secondary">{categoryName}</Text>
        </View>
      </View>
      <View style={{ width: PLACE_THUMBNAIL_SIZE, height: PLACE_THUMBNAIL_SIZE }}>
        <MediaPhoto uri={place.imageUrl} className="size-full rounded-sm" />
      </View>
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
