import { useRef, useState } from "react";
import { FlatList, Pressable, View, type NativeScrollEvent, type NativeSyntheticEvent } from "react-native";
import { router } from "expo-router";
import { ChevronLeft, ChevronRight } from "lucide-react-native";

import { Icon } from "@/components/ui/icon";
import { MediaPhoto } from "@/components/ui/media-photo";
import { Text } from "@/components/ui/text";
import { formatCourseFacts, type FeaturedCourse } from "@/features/course/model";
import { cn } from "@/lib/utils";
import { AuthorAvatars } from "./author-avatars";

const COVER_HEIGHT = 218;
const HIGHLIGHT_OVERLAP = 53;
const THUMBNAIL_SIZE = 102;

// 손가락으로 한 장씩 넘기는 추천 코스 슬라이드(EXP-04). 아래 화살표와 같은 인덱스를 공유한다.
export function FeaturedCarousel({ courses, onEndReached }: { courses: FeaturedCourse[]; onEndReached?: () => void }) {
  const listRef = useRef<FlatList<FeaturedCourse>>(null);
  const [width, setWidth] = useState(0);
  const [scrollIndex, setIndex] = useState(0);
  const index = Math.min(scrollIndex, courses.length - 1);
  const first = index === 0;
  const last = index === courses.length - 1;

  function moveTo(next: number) {
    setIndex(next);
    listRef.current?.scrollToIndex({ index: next, animated: true });
  }

  function syncIndex(event: NativeSyntheticEvent<NativeScrollEvent>) {
    if (width > 0) {
      setIndex(Math.round(event.nativeEvent.contentOffset.x / width));
    }
  }

  return (
    <View className="gap-4 px-5 pb-8" testID="home.featured">
      <View
        onLayout={({ nativeEvent: { layout } }) => {
          setWidth(layout.width);
        }}
        className="rounded-lg"
      >
        {width > 0 && (
          <FlatList
            ref={listRef}
            data={courses}
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            keyExtractor={(course) => {
              return String(course.id);
            }}
            getItemLayout={(_, itemIndex) => {
              return { length: width, offset: width * itemIndex, index: itemIndex };
            }}
            onMomentumScrollEnd={syncIndex}
            onEndReached={onEndReached}
            onEndReachedThreshold={0.5}
            renderItem={({ item }) => {
              return (
                <View style={{ width }}>
                  <HeroCourseCard course={item} />
                </View>
              );
            }}
            testID="home.featured.list"
          />
        )}
      </View>
      <View className="flex-row items-center justify-center gap-8">
        <PagerButton label="이전 추천 코스" disabled={first} onPress={() => {
          return moveTo(index - 1);
        }} testID="home.featured.prev" icon={ChevronLeft} />
        <View className="flex-row items-center gap-1" accessibilityLabel={`${index + 1} / ${courses.length}`}>
          <Text className="text-h3 text-foreground tabular-nums">{index + 1}</Text>
          <Text className="text-body text-muted-foreground tabular-nums">{`/ ${courses.length}`}</Text>
        </View>
        <PagerButton label="다음 추천 코스" disabled={last && !onEndReached} onPress={() => {
          if (last) {
            onEndReached?.();
          } else {
            moveTo(index + 1);
          }
        }} testID="home.featured.next" icon={ChevronRight} />
      </View>
    </View>
  );
}

function HeroCourseCard({ course }: { course: FeaturedCourse }) {
  return (
    <View className="overflow-hidden rounded-lg border border-border bg-card">
      <View className="relative gap-4 bg-primary-pressed p-5" style={{ minHeight: COVER_HEIGHT }}>
        <View pointerEvents="none" className="absolute inset-0">
          <MediaPhoto uri={course.thumbnailUrl} className="size-full rounded-none bg-primary-pressed" />
          <View className="absolute inset-0 bg-black/50" />
        </View>
        <Text className="text-h1 text-primary-foreground">{course.title}</Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${course.title} 자세히 보기`}
          hitSlop={{ top: 7, bottom: 7 }}
          onPress={() => {
            return router.push({ pathname: "/courses/[courseId]", params: { courseId: String(course.id) } });
          }}
          className="h-8 flex-row items-center justify-center gap-2 self-start rounded-full bg-card px-4 active:bg-accent"
          testID="home.featured.open"
        >
          <Text className="text-label-sm text-foreground">자세히 보기</Text>
          <Icon as={ChevronRight} size={12} className="text-foreground" />
        </Pressable>
      </View>
      <View className="flex-row justify-center gap-3" style={{ marginTop: -HIGHLIGHT_OVERLAP }}>
        {course.previewPlaces.map((item) => {
          return (
            <View key={item.visitId} className="gap-2" style={{ width: THUMBNAIL_SIZE }}>
              <View style={{ width: THUMBNAIL_SIZE, height: THUMBNAIL_SIZE }}>
                <MediaPhoto uri={item.imageUrl} accessibilityLabel={`${item.name} 사진`} className="size-full" />
              </View>
              <View>
                <Text numberOfLines={1} className="text-label text-foreground">{item.name}</Text>
                <View className="flex-row items-center gap-1">
                  <Text className="text-caption font-medium text-primary">{`${item.day}일차`}</Text>
                  {item.categoryName !== null && <Text numberOfLines={1} className="shrink text-caption text-muted-foreground">{item.categoryName}</Text>}
                </View>
              </View>
            </View>
          );
        })}
      </View>
      <View className="flex-row items-center gap-2 px-2 pb-2 pt-2">
        <AuthorAvatars authors={course.authors} size={20} overlap={4} />
        <Text className="shrink text-caption text-foreground">{`${course.regionName} · ${formatCourseFacts(course)}`}</Text>
      </View>
    </View>
  );
}

type PagerButtonProps = {
  label: string;
  disabled: boolean;
  onPress: () => void;
  testID: string;
  icon: typeof ChevronLeft;
};

function PagerButton({ label, disabled, onPress, testID, icon }: PagerButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      className={cn("h-11 w-16 items-center justify-center rounded-sm border border-border bg-card active:bg-accent", disabled && "opacity-40")}
      testID={testID}
    >
      <Icon as={icon} size={20} className="text-icon" />
    </Pressable>
  );
}
