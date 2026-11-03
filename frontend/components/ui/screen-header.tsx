import { Pressable, View } from "react-native";
import { ChevronLeft } from "lucide-react-native";

import { Icon } from "./icon";
import { Text } from "./text";

type ScreenHeaderProps = {
  title: string;
  onBack: () => void;
};

export function ScreenHeader({ title, onBack }: ScreenHeaderProps) {
  return (
    <View className="h-14 flex-row items-center justify-between bg-card px-2">
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="뒤로 가기"
        onPress={onBack}
        className="size-11 items-center justify-center"
        testID="screen-header.back"
      >
        <Icon as={ChevronLeft} size={24} className="text-icon" />
      </Pressable>
      <Text accessibilityRole="header" numberOfLines={1} className="text-h3 text-foreground">{title}</Text>
      <View className="size-11" />
    </View>
  );
}
