import { ChevronRight, MapIcon, Search, Sparkles, type LucideIcon } from "lucide-react-native";
import { Pressable, View } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";

/** 장소를 담는 경로. `course` 다른 사람 코스, `ai` AI 추천(W10), `search` 직접 검색(W12). */
export type AddPlaceSource = "course" | "ai" | "search";

const ADD_PLACE_OPTIONS: readonly { source: AddPlaceSource; icon: LucideIcon; title: string; description: string }[] = [
  { source: "course", icon: MapIcon, title: "다른 사람 코스에서", description: "공개 코스에서 원하는 장소만 골라요" },
  { source: "ai", icon: Sparkles, title: "AI 추천 받기", description: "담은 장소와 동선이 맞는 곳을 추천해요" },
  { source: "search", icon: Search, title: "직접 검색하기", description: "장소 이름으로 찾아 바로 담아요" },
];

/** 장소 담기 안내 제목(Figma W09-A). */
export function AddPlaceHeading() {
  return (
    <View className="gap-1 pb-2">
      <Text accessibilityRole="header" className="text-h3 text-foreground">
        어디서 장소를 담을까요?
      </Text>
      <Text className="text-caption text-foreground-secondary">담은 장소는 일행 모두에게 바로 보여요</Text>
    </View>
  );
}

type AddPlaceOptionsProps = {
  onSelect: (source: AddPlaceSource) => void;
  /** 항목 testID 앞부분. 뒤에 `course`·`ai`·`search`가 붙는다. */
  testIDPrefix: string;
};

/** 장소를 담는 세 가지 경로(Figma W09-A). 시트와, 후보 장소가 없을 때의 목록 자리에서 함께 쓴다. */
export function AddPlaceOptions({ onSelect, testIDPrefix }: AddPlaceOptionsProps) {
  return (
    <View className="gap-2">
      {ADD_PLACE_OPTIONS.map((option) => {
        return (
          <Pressable
            key={option.source}
            accessibilityRole="button"
            accessibilityLabel={`${option.title}. ${option.description}`}
            className="flex-row items-center gap-3 rounded-md py-3 active:bg-accent"

            onPress={() => {
              onSelect(option.source);
            }}

            testID={`${testIDPrefix}${option.source}`}
          >
            <View className="h-touch w-touch items-center justify-center rounded-md bg-brand-muted">
              <Icon as={option.icon} size={22} className="text-icon-active" />
            </View>
            <View className="flex-1 gap-1">
              <Text className="text-label text-foreground">{option.title}</Text>
              <Text className="text-caption text-foreground-secondary">{option.description}</Text>
            </View>
            <Icon as={ChevronRight} size={20} className="text-icon" />
          </Pressable>
        );
      })}
    </View>
  );
}
