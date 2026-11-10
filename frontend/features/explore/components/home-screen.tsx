import { useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import { router } from "expo-router";
import { ChevronRight, Footprints } from "lucide-react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { DesignAsset } from "@/components/ui/design-asset";
import { Icon } from "@/components/ui/icon";
import { MediaPhoto } from "@/components/ui/media-photo";
import { SearchField } from "@/components/ui/search-field";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useCategories } from "@/features/common/api/queries";
import type { Category } from "@/features/common/model";
import { useCourseRecommendations, usePopularPlaces } from "@/features/course/api/queries";
import { getCourseErrorMessage } from "@/features/course/errors";
import type { PopularPlace } from "@/features/course/model";
import type { PlaceRefDto } from "@/features/room/model";
import { formatPlaceRefsParam } from "@/features/room/route-params";
import { cn } from "@/lib/utils";
import { categoryIconOf } from "./category-icons";
import { FeaturedCarousel } from "./featured-carousel";
import { PlaceLocationMap } from "./place-location-map";
import { InlineMessage } from "./query-states";
import { RoomSelectSheet } from "./room-select-sheet";

const RANKING_THUMBNAIL_SIZE = 64;

export function HomeScreen() {
  const [category, setCategory] = useState<string | null>(null);

  return (
    <SafeAreaView edges={["top"]} className="flex-1 bg-background" testID="home.screen">
      <View className="flex-row items-center gap-3 px-5 pb-4 pt-2">
        <DesignAsset source={require("@/assets/images/logo/logo-text.webp")} contentFit="contain" style={{ width: 90, height: 30 }} accessibilityLabel="여기록" />
        <Pressable
          accessibilityRole="search"
          accessibilityLabel="지역, 장소, 코스 검색"
          accessibilityHint="검색 화면을 열어요"
          onPress={() => {
            router.push("/courses");
          }}
          className="flex-1"
          testID="home.search"
        >
          <View className="pointer-events-none">
            <SearchField placeholder="지역, 장소, 코스 검색" accessible={false} />
          </View>
        </Pressable>
      </View>
      <ScrollView className="flex-1" contentContainerClassName="pb-4" keyboardShouldPersistTaps="handled">
        <FeaturedSection key={category ?? "all"} category={category} />
        <View className="gap-4 pb-4">
          <View className="h-2 bg-muted" />
          <Text accessibilityRole="header" className="px-5 pt-4 text-h3 text-foreground">어떤 곳을 찾으세요?</Text>
          <CategoryRankingSection picked={category} onSelect={setCategory} />
        </View>
        {__DEV__ && (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="카카오맵 테스트 화면 열기"
            onPress={() => {
              return router.push("/kakao-map-test");
            }}
            className="mx-5 mt-4 min-h-touch items-center justify-center rounded-md bg-muted px-4"
            testID="home.kakao-map-test"
          >
            <Text className="text-button text-foreground-secondary">카카오맵 테스트 (개발용)</Text>
          </Pressable>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

// #region 추천 코스

function FeaturedSection({ category }: { category: string | null }) {
  const recommendations = useCourseRecommendations(category);

  if (recommendations.isPending) {
    return (
      <View accessibilityLabel="추천 코스 불러오는 중" className="gap-4 px-5 pb-8 pt-3" testID="home.loading">
        <Skeleton className="h-56 w-full rounded-lg" />
        <Skeleton className="h-6 w-skeleton-heading" />
      </View>
    );
  }

  if (!recommendations.data) {
    return (
      <View className="px-5 pb-8 pt-4">
        <InlineMessage
          title="추천 코스를 불러오지 못했어요"
          description={getCourseErrorMessage(recommendations.error)}
          actionLabel="다시 시도"
          onAction={() => {
            return void recommendations.refetch();
          }}
          testID="home.error"
        />
      </View>
    );
  }

  const courses = recommendations.data.pages.flatMap((page) => {
    return page.courses;
  });

  if (courses.length === 0) {
    return (
      <View className="px-5 pb-8 pt-4">
        <InlineMessage title="아직 추천할 코스가 없어요" description="검색에서 다른 사람의 코스를 찾아보세요." testID="home.featured.empty" />
      </View>
    );
  }

  function loadMore() {
    void recommendations.fetchNextPage({ cancelRefetch: false });
  }

  const canLoadMore = recommendations.hasNextPage && !recommendations.isFetching && !recommendations.isFetchNextPageError;

  return (
    <>
      <FeaturedCarousel courses={courses} onEndReached={canLoadMore ? loadMore : undefined} />
      {recommendations.isFetchingNextPage && (
        <Text accessibilityLiveRegion="polite" className="px-5 pb-4 text-caption text-muted-foreground">추천 코스를 더 불러오는 중이에요</Text>
      )}
      {recommendations.isFetchNextPageError && (
        <View className="px-5 pb-4">
          <InlineMessage title="추천 코스를 더 불러오지 못했어요" description={getCourseErrorMessage(recommendations.error)} actionLabel="다시 시도" onAction={loadMore} />
        </View>
      )}
    </>
  );
}

// #endregion

// #region 카테고리·인기 장소

function CategoryRankingSection({ picked, onSelect }: { picked: string | null; onSelect: (code: string) => void }) {
  const categories = useCategories();

  if (categories.isPending) {
    return (
      <View accessibilityLabel="카테고리 불러오는 중" className="px-5">
        <Skeleton className="h-24 w-full" />
      </View>
    );
  }

  if (categories.isError) {
    return (
      <View className="px-5">
        <InlineMessage title="카테고리를 불러오지 못했어요" actionLabel="다시 시도" onAction={() => {
          return void categories.refetch();
        }} testID="home.categories.error" />
      </View>
    );
  }

  const category = picked ?? categories.data[0]?.code ?? null;

  return (
    <>
      <CategoryGrid categories={categories.data} selected={category} onSelect={onSelect} />
      <RankingSection category={category} />
    </>
  );
}

function CategoryGrid({ categories, selected, onSelect }: { categories: Category[]; selected: string | null; onSelect: (code: string) => void }) {
  return (
    <View className="flex-row flex-wrap gap-y-4 px-2">
      {categories.map((item) => {
        const active = item.code === selected;

        return (
          <Pressable
            key={item.code}
            accessibilityRole="button"
            accessibilityLabel={item.name}
            accessibilityState={{ selected: active }}
            onPress={() => {
              onSelect(item.code);
            }}
            className="w-1/4 items-center gap-2"
            testID={`home.category.${item.code}`}
          >
            <View className={cn("items-center justify-center rounded-lg", active ? "bg-accent" : "bg-muted")} style={{ width: 60, height: 60 }}>
              <Icon as={categoryIconOf(item.code)} size={26} className={active ? "text-primary" : "text-icon"} />
            </View>
            <Text className={cn("text-center text-caption", active ? "font-medium text-primary" : "text-foreground-secondary")}>{item.name}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/** 새 여행방에 이어 담을 때(API-029) 장소를 가리키는 값. 공급자를 모르거나 장소 식별값이 없으면 담을 수 없어 `null`이다. */
function toCarryOverPlaceRef(place: PopularPlace): PlaceRefDto | null {
  if (place.provider === "UNKNOWN" || !place.providerPlaceId) {
    return null;
  }

  return { provider: place.provider, providerPlaceId: place.providerPlaceId };
}

function RankingSection({ category }: { category: string | null }) {
  const ranking = usePopularPlaces(category);
  const [sheetPlace, setSheetPlace] = useState<PopularPlace>();
  const [notice, setNotice] = useState("");
  const carryOverPlaceRef = sheetPlace ? toCarryOverPlaceRef(sheetPlace) : null;

  return (
    <View className="gap-4" testID="home.ranking">
      <Text className="px-5 text-caption text-foreground-secondary">최근 30일 동안 코스에서 가장 많이 가져간 장소예요</Text>
      <View className="px-5">
        {ranking.isPending && (
          <View accessibilityLabel="인기 장소 불러오는 중" className="gap-3 py-2">
            {[0, 1, 2].map((key) => {
              return <Skeleton key={key} className="h-16 w-full" />;
            })}
          </View>
        )}
        {ranking.isError && (
          <InlineMessage title="인기 장소를 불러오지 못했어요" description={getCourseErrorMessage(ranking.error)} actionLabel="다시 시도" onAction={() => {
            return void ranking.refetch();
          }} />
        )}
        {ranking.data?.length === 0 && (
          <InlineMessage title="아직 가져간 장소가 없어요" description="다른 분류를 골라 보세요." testID="home.ranking.empty" />
        )}
        {ranking.data?.map((place, index) => {
          return (
            <RankingRow
              key={place.key}
              place={place}
              last={index === ranking.data.length - 1}
              onPress={() => {
                setNotice("");
                setSheetPlace(place);
              }}
            />
          );
        })}
      </View>
      {notice !== "" && (
        <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="mx-5 rounded-md bg-brand-muted px-4 py-3 text-label text-primary" testID="home.notice">{notice}</Text>
      )}
      <RoomSelectSheet
        visible={sheetPlace !== undefined}
        placeIds={sheetPlace ? [sheetPlace.key] : []}
        placeNames={sheetPlace ? [[sheetPlace.name, sheetPlace.areaName].filter(Boolean).join(" · ")] : []}
        onClose={() => {
          setSheetPlace(undefined);
        }}
        onAdded={(result) => {
          setSheetPlace(undefined);
          setNotice(`${result.roomName}에 ${result.addedCount}곳을 담았어요`);
        }}
        onCreateRoom={sheetPlace && carryOverPlaceRef !== null ? () => {
          setSheetPlace(undefined);

          router.push({
            pathname: "/rooms/new",
            params: { placeRefs: formatPlaceRefsParam([carryOverPlaceRef]), placeNames: sheetPlace.name },
          });
        } : undefined}
      >
        {sheetPlace && <PlaceLocationMap place={sheetPlace} />}
      </RoomSelectSheet>
    </View>
  );
}

function RankingRow({ place, last, onPress }: { place: PopularPlace; last: boolean; onPress: () => void }) {
  const top = place.rank <= 3;
  const area = place.areaName ?? "지역 정보 없음";

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${place.rank}위 ${place.name}, ${area}, 최근 30일 여행방 ${place.importCount30d}곳이 가져감`}
      accessibilityHint="위치를 보고 여행방에 담아요"
      onPress={onPress}
      className={cn("flex-row items-center gap-3 border-border py-3 active:bg-muted", !last && "border-b")}
      testID={`home.ranking.${place.rank}`}
    >
      <Text className={cn("w-5 text-center text-h2 tabular-nums", top ? "text-primary" : "text-muted-foreground")}>{place.rank}</Text>
      <View style={{ width: RANKING_THUMBNAIL_SIZE, height: RANKING_THUMBNAIL_SIZE }}>
        <MediaPhoto uri={place.imageUrl} className="size-full" />
      </View>
      <View className="flex-1 gap-1">
        <Text numberOfLines={1} className="text-label text-foreground">{place.name}</Text>
        <Text numberOfLines={1} className="text-caption text-foreground-secondary">{area}</Text>
        <View className="flex-row items-center gap-1">
          <Icon as={Footprints} size={14} className="text-highlight" />
          <Text className="text-caption text-highlight tabular-nums">{`여행방 ${place.importCount30d}곳이 가져갔어요`}</Text>
        </View>
      </View>
      <Icon as={ChevronRight} size={20} className="text-icon" />
    </Pressable>
  );
}

// #endregion
