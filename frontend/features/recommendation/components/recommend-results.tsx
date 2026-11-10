import { FlatList, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import type { Category } from "@/features/common/model";
import { findCategoryName } from "@/features/place/model";
import { RecommendedPlaceCard, type RecommendedPlaceCardState } from "@/features/recommendation/components/recommended-place-card";
import type { PlaceRecommendations, RecommendedPlace } from "@/features/recommendation/model";

type RecommendResultsProps = {
  result: PlaceRecommendations;
  categories: readonly Category[];
  /** 사용자가 고른 카테고리 이름. "○○ 기준" 줄에 쓴다. */
  criteria: readonly string[];
  addedPlaceKeys: ReadonlySet<string>;
  excludedPlaceKeys: ReadonlySet<string>;
  onToggle: (placeKey: string) => void;
  onReselect: () => void;
};

/** AI 추천 결과(Figma W11). 추천은 자동으로 담기지 않고, 고른 장소만 하단 버튼으로 담는다. */
export function RecommendResults({ result, categories, criteria, addedPlaceKeys, excludedPlaceKeys, onToggle, onReselect }: RecommendResultsProps) {
  if (result.places.length === 0) {
    return (
      <View className="flex-1 items-center justify-center gap-4 px-5" testID="recommend.empty">
        <View className="items-center gap-2">
          <Text accessibilityRole="header" className="text-center text-h3 text-foreground">
            추천할 장소를 찾지 못했어요
          </Text>
          <Text className="text-center text-body-sm text-foreground-secondary">조건을 바꿔 다시 추천받아 보세요.</Text>
        </View>
        <Button variant="outline" onPress={onReselect} testID="recommend.reselect">
          <Text>조건 다시 고르기</Text>
        </Button>
      </View>
    );
  }

  function getCardState({ place }: RecommendedPlace): RecommendedPlaceCardState {
    if (addedPlaceKeys.has(place.key)) {
      return "added";
    }

    if (place.ref === null) {
      return "unavailable";
    }

    return excludedPlaceKeys.has(place.key) ? "unselected" : "selected";
  }

  return (
    <FlatList
      className="flex-1"
      contentContainerClassName="px-5 pb-6 pt-4"
      data={result.places}
      extraData={{ addedPlaceKeys, excludedPlaceKeys }}
      keyExtractor={(recommendation) => {
        return recommendation.place.key;
      }}
      renderItem={({ item }) => {
        return (
          <RecommendedPlaceCard
            recommendation={item}
            categoryName={findCategoryName(categories, item.place.categoryCode)}
            state={getCardState(item)}
            onToggle={onToggle}
          />
        );
      }}
      ItemSeparatorComponent={CardSeparator}
      ListHeaderComponent={
        <View className="gap-1 pb-4">
          <Text accessibilityRole="header" className="text-h2 text-foreground">
            {`동선에 맞는 ${result.places.length}곳을 찾았어요`}
          </Text>
          {criteria.length > 0 ? (
            <Text className="text-caption text-foreground-secondary">{`${criteria.join(" ")} 기준`}</Text>
          ) : null}
        </View>
      }
      testID="recommend.results"
    />
  );
}

function CardSeparator() {
  return <View className="h-4" />;
}
