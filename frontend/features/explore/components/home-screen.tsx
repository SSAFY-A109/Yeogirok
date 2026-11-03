import { useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import { router } from "expo-router";
import { ChevronRight, Footprints } from "lucide-react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { DesignAsset } from "@/components/ui/design-asset";
import { Icon } from "@/components/ui/icon";
import { SearchField } from "@/components/ui/search-field";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { cn } from "@/lib/utils";
import { useHome, useRanking } from "../api/queries";
import type { CategoryId, RankedPlace } from "../model";
import { categoryIcons } from "./category-icons";
import { FeaturedCarousel } from "./featured-carousel";
import { PhotoPlaceholder, toneAt } from "./photo-placeholder";
import { PlaceLocationMap } from "./place-location-map";
import { InlineMessage } from "./query-states";
import { RoomSelectSheet } from "./room-select-sheet";

export function HomeScreen() {
  const home = useHome();
  const [category, setCategory] = useState<CategoryId>("NATURE");
  const [subcategory, setSubcategory] = useState("전체");

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
        {home.isPending && <HomeSkeleton />}
        {home.isError && (
          <View className="px-5 pt-4">
            <InlineMessage
              title="코스를 불러오지 못했어요"
              description="잠시 후 다시 시도해 주세요."
              actionLabel="다시 시도"
              onAction={() => {
                return void home.refetch();
              }}
              testID="home.error"
            />
          </View>
        )}
        {home.data && (
          <>
            <FeaturedCarousel courses={home.data.featured} />
            <View className="gap-4 pb-4">
              <View className="h-2 bg-muted" />
              <Text accessibilityRole="header" className="px-5 pt-4 text-h3 text-foreground">어떤 곳을 찾으세요?</Text>
              <View className="flex-row flex-wrap gap-y-4 px-2">
                {home.data.categories.map((item) => {
                  const selected = item.id === category;

                  return (
                    <Pressable
                      key={item.id}
                      accessibilityRole="button"
                      accessibilityLabel={item.label}
                      accessibilityState={{ selected }}
                      onPress={() => {
                        setCategory(item.id);
                        setSubcategory("전체");
                      }}
                      className="w-1/4 items-center gap-2"
                      testID={`home.category.${item.id}`}
                    >
                      <View className={cn("items-center justify-center rounded-lg", selected ? "bg-accent" : "bg-muted")} style={{ width: 60, height: 60 }}>
                        <Icon as={categoryIcons[item.icon]} size={26} className={selected ? "text-primary" : "text-icon"} />
                      </View>
                      <Text className={cn("text-center text-caption", selected ? "font-medium text-primary" : "text-foreground-secondary")}>{item.label}</Text>
                    </Pressable>
                  );
                })}
              </View>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerClassName="gap-2 px-5" className="grow-0">
                {home.data.subcategories[category].map((name) => {
                  const selected = name === subcategory;

                  return (
                    <Pressable
                      key={name}
                      accessibilityRole="button"
                      accessibilityState={{ selected }}
                      hitSlop={{ top: 6, bottom: 6 }}
                      onPress={() => {
                        return setSubcategory(name);
                      }}
                      className={cn("h-8 items-center justify-center rounded-full px-3", selected ? "bg-foreground" : "bg-muted")}
                    >
                      <Text className={cn("text-label-sm", selected ? "text-primary-foreground" : "text-foreground")}>{name}</Text>
                    </Pressable>
                  );
                })}
              </ScrollView>
              <RankingSection category={category} subcategory={subcategory} />
            </View>
          </>
        )}
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

function RankingSection({ category, subcategory }: { category: CategoryId; subcategory: string }) {
  const ranking = useRanking(category, subcategory);
  const [sheetPlace, setSheetPlace] = useState<RankedPlace>();
  const [notice, setNotice] = useState("");

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
          <InlineMessage title="인기 장소를 불러오지 못했어요" actionLabel="다시 시도" onAction={() => {
            return void ranking.refetch();
          }} />
        )}
        {ranking.data?.length === 0 && (
          <InlineMessage title="아직 가져간 장소가 없어요" description="다른 분류를 골라 보세요." testID="home.ranking.empty" />
        )}
        {ranking.data?.map((place, rank) => {
          return (
            <Pressable
              key={place.id}
              accessibilityRole="button"
              accessibilityLabel={`${rank + 1}위 ${place.name}, ${place.area}, 코스 ${place.courseCount}개에 담김`}
              accessibilityHint="위치를 보고 여행방에 담아요"
              onPress={() => {
                setNotice("");
                setSheetPlace(place);
              }}
              className={cn("flex-row items-center gap-3 border-border py-3 active:bg-muted", rank < ranking.data.length - 1 && "border-b")}
              testID={`home.ranking.${place.id}`}
            >
              <Text className={cn("w-5 text-center text-h2 tabular-nums", rank < 3 ? "text-primary" : "text-muted-foreground")}>{rank + 1}</Text>
              <PhotoPlaceholder size={64} tone={toneAt(rank)} />
              <View className="flex-1 gap-1">
                <Text className="text-label text-foreground">{place.name}</Text>
                <Text className="text-caption text-foreground-secondary">{place.area}</Text>
                <View className="flex-row items-center gap-1">
                  <Icon as={Footprints} size={14} className="text-highlight" />
                  <Text className="text-caption text-highlight">{`코스 ${place.courseCount}개에 담겼어요`}</Text>
                </View>
              </View>
              <Icon as={ChevronRight} size={20} className="text-icon" />
            </Pressable>
          );
        })}
      </View>
      {notice !== "" && (
        <Text accessibilityRole="alert" accessibilityLiveRegion="polite" className="mx-5 rounded-md bg-brand-muted px-4 py-3 text-label text-primary" testID="home.notice">{notice}</Text>
      )}
      <RoomSelectSheet
        visible={sheetPlace !== undefined}
        placeIds={sheetPlace ? [sheetPlace.id] : []}
        placeNames={sheetPlace ? [`${sheetPlace.name} · ${sheetPlace.area}`] : []}
        onClose={() => {
          setSheetPlace(undefined);
        }}
        onAdded={(result) => {
          setSheetPlace(undefined);
          setNotice(`${result.roomName}에 ${result.addedCount}곳을 담았어요`);
        }}
      >
        {sheetPlace && <PlaceLocationMap place={sheetPlace} />}
      </RoomSelectSheet>
    </View>
  );
}

function HomeSkeleton() {
  return (
    <View accessibilityLabel="불러오는 중" className="gap-4 px-5 pt-3" testID="home.loading">
      <Skeleton className="h-56 w-full rounded-lg" />
      <Skeleton className="h-6 w-skeleton-heading" />
      <Skeleton className="h-24 w-full" />
    </View>
  );
}
