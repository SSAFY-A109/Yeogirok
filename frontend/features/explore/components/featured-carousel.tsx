import { useRef, useState } from "react";
import { FlatList, Pressable, View, type NativeScrollEvent, type NativeSyntheticEvent } from "react-native";
import { router } from "expo-router";
import { ChevronLeft, ChevronRight } from "lucide-react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { cn } from "@/lib/utils";
import type { FeaturedCourse } from "../model";
import { PhotoPlaceholder, toneAt } from "./photo-placeholder";

const COVER_HEIGHT = 218;
const HIGHLIGHT_OVERLAP = 53;
const THUMBNAIL_SIZE = 102;

// 손가락으로 한 장씩 넘기는 추천 코스 슬라이드(EXP-04). 아래 화살표와 같은 인덱스를 공유한다.
export function FeaturedCarousel({ courses }: { courses: FeaturedCourse[] }) {
  const listRef = useRef<FlatList<FeaturedCourse>>(null);
  const [width, setWidth] = useState(0);
  const [index, setIndex] = useState(0);
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
              return course.id;
            }}
            getItemLayout={(_, itemIndex) => {
              return { length: width, offset: width * itemIndex, index: itemIndex };
            }}
            onMomentumScrollEnd={syncIndex}
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
        <PagerButton label="다음 추천 코스" disabled={last} onPress={() => {
          return moveTo(index + 1);
        }} testID="home.featured.next" icon={ChevronRight} />
      </View>
    </View>
  );
}

function HeroCourseCard({ course }: { course: FeaturedCourse }) {
  return (
    <View className="overflow-hidden rounded-lg border border-border bg-card">
      <View className="gap-4 bg-primary-pressed p-5" style={{ minHeight: COVER_HEIGHT }}>
        <Text className="text-h1 text-primary-foreground">{course.title}</Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`${course.title} 자세히 보기`}
          hitSlop={{ top: 7, bottom: 7 }}
          onPress={() => {
            return router.push({ pathname: "/courses/[courseId]", params: { courseId: course.id } });
          }}
          className="h-8 flex-row items-center justify-center gap-2 self-start rounded-full bg-card px-4 active:bg-accent"
          testID="home.featured.open"
        >
          <Text className="text-label-sm text-foreground">자세히 보기</Text>
          <Icon as={ChevronRight} size={12} className="text-foreground" />
        </Pressable>
      </View>
      <View className="flex-row justify-center gap-3" style={{ marginTop: -HIGHLIGHT_OVERLAP }}>
        {course.highlights.map((item, position) => {
          return (
            <View key={`${item.day}-${item.name}`} className="gap-2" style={{ width: THUMBNAIL_SIZE }}>
              <PhotoPlaceholder size={THUMBNAIL_SIZE} tone={toneAt(position)} />
              <View>
                <Text numberOfLines={1} className="text-label text-foreground">{item.name}</Text>
                <View className="flex-row items-center gap-1">
                  <Text className="text-caption font-medium text-primary">{`${item.day}일차`}</Text>
                  <Text numberOfLines={1} className="shrink text-caption text-muted-foreground">{item.tag}</Text>
                </View>
              </View>
            </View>
          );
        })}
      </View>
      <View className="flex-row items-center gap-2 px-2 pb-2 pt-2">
        <View className="size-5 items-center justify-center rounded-full bg-brand-muted">
          <Text className="text-micro text-primary">{course.author}</Text>
        </View>
        <Text className="text-caption text-foreground">{`장소 ${course.placeCount}곳 / ${course.duration} / ${course.transport}`}</Text>
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
