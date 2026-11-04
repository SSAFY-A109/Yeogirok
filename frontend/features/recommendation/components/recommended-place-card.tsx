import { Sparkles } from "lucide-react-native";
import { Pressable, View } from "react-native";

import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { Icon } from "@/components/ui/icon";
import { MediaPhoto } from "@/components/ui/media-photo";
import { Text } from "@/components/ui/text";
import type { RecommendedPlace } from "@/features/recommendation/model";
import { cn } from "@/lib/utils";

/** `added`: 이미 여행방에 담긴 곳. 체크된 채 비활성으로 보인다. */
export type RecommendedPlaceCardState = "unselected" | "selected" | "added";

type RecommendedPlaceCardProps = {
  place: RecommendedPlace;
  state: RecommendedPlaceCardState;
  onToggle: (placeId: number) => void;
};

/** 카드 보조 줄(카테고리 이름 + 서버가 준 이동 문구). 둘 다 없으면 `null`. */
function getRecommendedPlaceSubtitle(place: RecommendedPlace): string | null {
  const parts = [place.category?.name, place.routeHint].filter((part): part is string => {
    return Boolean(part);
  });

  return parts.length > 0 ? parts.join(" ") : null;
}

/** AI 추천 장소 카드(Figma W11 `RecCard`). 카드 전체를 눌러 담을 곳을 고른다. */
export function RecommendedPlaceCard({ place, state, onToggle }: RecommendedPlaceCardProps) {
  const isAdded = state === "added";
  const isSelected = state === "selected";
  const subtitle = getRecommendedPlaceSubtitle(place);

  const accessibilityLabel = [place.name, subtitle, place.reason]
    .filter((part): part is string => {
      return Boolean(part);
    })
    .join(", ");

  const handleToggle = () => {
    onToggle(place.placeId);
  };

  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={accessibilityLabel}
      accessibilityHint={isAdded ? "이미 담은 장소예요" : undefined}
      accessibilityState={{ checked: state !== "unselected", disabled: isAdded }}
      className={cn("gap-3 rounded-md border border-border bg-card p-4", isSelected && "border-2 border-ring bg-accent")}
      disabled={isAdded}
      onPress={handleToggle}
      testID={`recommend.card-${place.placeId}`}
    >
      <View className="flex-row items-center gap-3">
        <MediaPhoto uri={place.photoUrl} className="size-14" />
        <View className="min-w-0 flex-1 gap-1">
          <Text className={cn("text-label text-foreground", isAdded && "text-muted-foreground")} numberOfLines={1}>
            {place.name}
          </Text>
          {subtitle ? <Text className="text-caption text-foreground-secondary">{subtitle}</Text> : null}
        </View>
        {isAdded ? (
          <Badge>
            <Text>담음</Text>
          </Badge>
        ) : null}
        <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <Checkbox checked={state !== "unselected"} disabled={isAdded} onCheckedChange={handleToggle} />
        </View>
      </View>
      {place.reason ? (
        <View className={cn("flex-row items-start gap-2 rounded-sm bg-muted p-3", isSelected && "bg-card")}>
          <Icon as={Sparkles} size={14} className="mt-0.5 text-icon-active" />
          <Text className="flex-1 text-caption text-foreground-secondary">{place.reason}</Text>
        </View>
      ) : null}
    </Pressable>
  );
}
