import { Fragment, type ReactNode } from "react";
import { View } from "react-native";

import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { Text } from "@/components/ui/text";
import type { MergedSearchItem } from "@/features/place/model";

export type MergedSearchState = { status: "searching" } | { status: "failed" } | { status: "ready"; items: MergedSearchItem[] };

type MergedSearchResultsProps = {
  state: MergedSearchState;
  renderItem: (item: MergedSearchItem, index: number) => ReactNode;
  onRetry: () => void;
};

/** 지도(카카오) 검색 순서로 보여 주는 검색 결과. 투어API에도 있는 곳은 투어API 장소로, 없는 곳은 직접 추가할 장소로 보인다. */
export function MergedSearchResults({ state, renderItem, onRetry }: MergedSearchResultsProps) {
  if (state.status === "searching") {
    return (
      <View accessibilityLiveRegion="polite" className="flex-row items-center gap-2 px-5 pt-3" testID="place-search.map-loading">
        <Spinner />
        <Text className="text-caption text-foreground-secondary">지도에서 찾는 중이에요</Text>
      </View>
    );
  }

  if (state.status === "failed") {
    return (
      <View className="items-start gap-2 px-5 pt-3" testID="place-search.map-error">
        <Text accessibilityRole="alert" className="text-caption text-foreground-secondary">
          지도 검색을 불러오지 못했어요
        </Text>
        <Button variant="outline" onPress={onRetry} testID="place-search.map-retry">
          <Text>다시 시도</Text>
        </Button>
      </View>
    );
  }

  if (state.items.length === 0) {
    return null;
  }

  const hasMapPlace = state.items.some((item) => {
    return item.kind === "map";
  });

  return (
    <View testID="place-search.merged-results">
      <View className="gap-1 px-5 pb-1 pt-3">
        <Text className="text-label text-foreground-secondary" testID="place-search.merged-count">
          {`검색 결과 ${state.items.length}곳`}
        </Text>
        {hasMapPlace ? <Text className="text-caption text-foreground-secondary">사진이 없는 곳은 직접 추가로 담겨요</Text> : null}
      </View>
      {state.items.map((item, index) => {
        return (
          <Fragment key={item.place.key}>
            {index > 0 ? <View className="mx-5 h-px bg-border" /> : null}
            {renderItem(item, index)}
          </Fragment>
        );
      })}
    </View>
  );
}
