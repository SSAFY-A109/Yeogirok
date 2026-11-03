import { FlatList, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { RecommendedPlaceCard, type RecommendedPlaceCardState } from "@/features/recommendation/components/recommended-place-card";
import type { PlaceRecommendations } from "@/features/recommendation/model";

type RecommendResultsProps = {
  result: PlaceRecommendations;
  addedPlaceIds: ReadonlySet<number>;
  excludedPlaceIds: ReadonlySet<number>;
  onToggle: (placeId: number) => void;
  onReselect: () => void;
};

/** AI 추천 결과(Figma W11). 추천은 자동으로 담기지 않고, 고른 장소만 하단 버튼으로 담는다. */
export function RecommendResults({ result, addedPlaceIds, excludedPlaceIds, onToggle, onReselect }: RecommendResultsProps) {
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

  function getCardState(placeId: number): RecommendedPlaceCardState {
    if (addedPlaceIds.has(placeId)) {
      return "added";
    }

    return excludedPlaceIds.has(placeId) ? "unselected" : "selected";
  }

  return (
    <FlatList
      className="flex-1"
      contentContainerClassName="px-5 pb-6 pt-4"
      data={result.places}
      extraData={{ addedPlaceIds, excludedPlaceIds }}
      keyExtractor={(place) => {
        return String(place.placeId);
      }}
      renderItem={({ item }) => {
        return <RecommendedPlaceCard place={item} state={getCardState(item.placeId)} onToggle={onToggle} />;
      }}
      ItemSeparatorComponent={CardSeparator}
      ListHeaderComponent={
        <View className="gap-1 pb-4">
          <Text accessibilityRole="header" className="text-h2 text-foreground">
            {`동선에 맞는 ${result.places.length}곳을 찾았어요`}
          </Text>
          {result.criteria.length > 0 ? (
            <Text className="text-caption text-foreground-secondary">{`${result.criteria.join(" ")} 기준`}</Text>
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
