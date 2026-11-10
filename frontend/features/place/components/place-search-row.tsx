import { Pressable, View } from "react-native";

import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { MediaPhoto } from "@/components/ui/media-photo";
import { Text } from "@/components/ui/text";
import type { Place } from "@/features/place/model";
import { cn } from "@/lib/utils";

/**
 * `added`: 이미 여행방에 담긴 곳. 체크된 채 비활성으로 보인다.
 * `unavailable`: 장소 식별값(`providerPlaceId`)이 없어 담을 수 없는 곳. 체크 없이 비활성으로 보인다.
 */
export type PlaceSearchRowState = "unselected" | "selected" | "added" | "unavailable";

type PlaceSearchRowProps = {
  place: Place;
  /** 공통 카테고리 목록에서 찾은 이름. 못 찾았으면 `null`. */
  categoryName: string | null;
  state: PlaceSearchRowState;
  onToggle: (place: Place) => void;
};

const DISABLED_HINT: Readonly<Partial<Record<PlaceSearchRowState, string>>> = {
  added: "이미 담은 장소예요",
  unavailable: "담을 수 없는 장소예요",
};

/** 결과 행 보조 줄(카테고리 이름 + 주소). 둘 다 없으면 `null`. */
function getPlaceSubtitle(categoryName: string | null, address: string | null): string | null {
  const parts = [categoryName, address].filter((part): part is string => {
    return Boolean(part);
  });

  return parts.length > 0 ? parts.join(" ") : null;
}

/** 여행지 검색 결과 한 줄(Figma W12 `Result`). 행 전체를 눌러 고른다. */
export function PlaceSearchRow({ place, categoryName, state, onToggle }: PlaceSearchRowProps) {
  const isAdded = state === "added";
  const isDisabled = isAdded || state === "unavailable";
  const isChecked = state === "selected" || isAdded;
  const subtitle = getPlaceSubtitle(categoryName, place.address);

  const handleToggle = () => {
    onToggle(place);
  };

  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={subtitle ? `${place.name}, ${subtitle}` : place.name}
      accessibilityHint={DISABLED_HINT[state]}
      accessibilityState={{ checked: isChecked, disabled: isDisabled }}
      className="mx-5 flex-row items-center gap-3 py-3 active:bg-muted"
      disabled={isDisabled}
      onPress={handleToggle}
      testID={`place-search.row-${place.key}`}
    >
      <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <Checkbox checked={isChecked} disabled={isDisabled} onCheckedChange={handleToggle} />
      </View>
      <MediaPhoto uri={place.photoUrl} className={cn("h-input w-input", isAdded && "bg-muted opacity-20")} />
      <View className="min-w-0 flex-1 gap-1">
        <Text className={cn("text-label text-foreground", isDisabled && "text-muted-foreground")} numberOfLines={1}>
          {place.name}
        </Text>
        {subtitle ? (
          <Text className="text-caption text-foreground-secondary" numberOfLines={1}>
            {subtitle}
          </Text>
        ) : null}
      </View>
      {isAdded ? (
        <Badge>
          <Text>담음</Text>
        </Badge>
      ) : null}
    </Pressable>
  );
}
