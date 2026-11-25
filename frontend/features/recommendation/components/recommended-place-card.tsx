import { Sparkles } from "lucide-react-native";
import { Pressable, View } from "react-native";

import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { Icon } from "@/components/ui/icon";
import { MediaPhoto } from "@/components/ui/media-photo";
import { Text } from "@/components/ui/text";
import type { RecommendedPlace } from "@/features/recommendation/model";
import { cn } from "@/lib/utils";

/**
 * `added`: 이미 여행방에 담긴 곳. 체크된 채 비활성으로 보인다.
 * `unavailable`: 장소 식별값(`providerPlaceId`)이 없어 담을 수 없는 곳. 체크 없이 비활성으로 보인다.
 */
export type RecommendedPlaceCardState = "unselected" | "selected" | "added" | "unavailable";

type RecommendedPlaceCardProps = {
  recommendation: RecommendedPlace;
  /** 공통 카테고리 목록에서 찾은 이름. 못 찾았으면 `null`. */
  categoryName: string | null;
  state: RecommendedPlaceCardState;
  onToggle: (placeKey: string) => void;
};

const DISABLED_HINT: Readonly<Partial<Record<RecommendedPlaceCardState, string>>> = {
  added: "이미 담은 장소예요",
  unavailable: "담을 수 없는 장소예요",
};

/** AI 추천 장소 카드(Figma W11 `RecCard`). 카드 전체를 눌러 담을 곳을 고른다. */
export function RecommendedPlaceCard({ recommendation, categoryName, state, onToggle }: RecommendedPlaceCardProps) {
  const { place, reason } = recommendation;
  const isAdded = state === "added";
  const isDisabled = isAdded || state === "unavailable";
  const isSelected = state === "selected";
  const isChecked = isSelected || isAdded;

  const accessibilityLabel = [place.name, categoryName, reason]
    .filter((part): part is string => {
      return Boolean(part);
    })
    .join(", ");

  const handleToggle = () => {
    onToggle(place.key);
  };

  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={accessibilityLabel}
      accessibilityHint={DISABLED_HINT[state]}
      accessibilityState={{ checked: isChecked, disabled: isDisabled }}
      className={cn("gap-3 rounded-md border border-border bg-card p-4", isSelected && "border-2 border-ring bg-accent")}
      disabled={isDisabled}
      onPress={handleToggle}
      testID={`recommend.card-${place.key}`}
    >
      <View className="flex-row items-center gap-3">
        <MediaPhoto uri={place.photoUrl} className="size-14" />
        <View className="min-w-0 flex-1 gap-1">
          <Text className={cn("text-label text-foreground", isDisabled && "text-muted-foreground")} numberOfLines={1}>
            {place.name}
          </Text>
          {categoryName ? <Text className="text-caption text-foreground-secondary">{categoryName}</Text> : null}
        </View>
        {isAdded ? (
          <Badge>
            <Text>담음</Text>
          </Badge>
        ) : null}
        <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <Checkbox checked={isChecked} disabled={isDisabled} onCheckedChange={handleToggle} />
        </View>
      </View>
      {reason ? (
        <View className={cn("flex-row items-start gap-2 rounded-sm bg-muted p-3", isSelected && "bg-card")}>
          <Icon as={Sparkles} size={14} className="mt-0.5 text-icon-active" />
          <Text className="flex-1 text-caption text-foreground-secondary">{reason}</Text>
        </View>
      ) : null}
    </Pressable>
  );
}
