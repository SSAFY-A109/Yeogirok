import { List, Map as MapIcon } from "lucide-react-native";
import { Pressable } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";

/** 보이는 높이(40)를 터치 높이 44로 넓히는 여백. */
const HIT_SLOP = { top: 2, bottom: 2 };

type MapViewToggleProps = {
  /** 누르면 넘어갈 보기. `map`이면 "지도로 보기", `list`면 "목록으로 보기"다. */
  target: "map" | "list";
  onPress: () => void;
  testID: string;
};

/** 목록과 지도를 오가는 떠 있는 버튼(Figma W09·W09-B·W14 `Map view toggle (floating)`). 목록이나 지도 위 아래쪽 가운데에 띄운다. */
export function MapViewToggle({ target, onPress, testID }: MapViewToggleProps) {
  const label = target === "map" ? "지도로 보기" : "목록으로 보기";

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      className="h-10 flex-row items-center gap-2 rounded-full bg-foreground px-4 active:bg-foreground-secondary"
      hitSlop={HIT_SLOP}
      onPress={onPress}
      testID={testID}
    >
      <Icon as={target === "map" ? MapIcon : List} size={16} className="text-primary-foreground" />
      <Text className="text-label text-primary-foreground">{label}</Text>
    </Pressable>
  );
}
