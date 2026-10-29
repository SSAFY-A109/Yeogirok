import type { ImageSource } from "expo-image";
import { Pressable, View, type ViewProps } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { getColors } from "@/lib/theme";
import { cn } from "@/lib/utils";
import { assets, DesignAsset } from "./design-asset";
import { Text } from "./text";

export const tabBarIcons = { house: assets.house, users: assets.users, scanEye: assets.scanEye, user: assets.user, map: assets.map };

export type TabBarItem = {
  key: string;
  label: string;
  icon: ImageSource | number;
  disabled?: boolean;
  accessibilityLabel?: string;
  testID?: string;
};

type TabBarProps = ViewProps & {
  items: readonly TabBarItem[];
  value: string;
  onValueChange: (value: string) => void;
};

export function TabBar({ items, value, onValueChange, className, style, ...props }: TabBarProps) {
  const insets = useSafeAreaInsets();
  const colors = getColors("light");

  return (
    <View accessibilityRole="tablist" className={cn("border-t border-border bg-card", className)} style={[{ paddingBottom: insets.bottom }, style]} {...props}>
      <View className="h-14 flex-row px-2 pt-2">
        {items.map((item) => {
          const selected = item.key === value;
          return (
            <Pressable key={item.key} accessibilityRole="tab" accessibilityLabel={item.accessibilityLabel ?? item.label} accessibilityState={{ selected, disabled: item.disabled }} disabled={item.disabled} testID={item.testID} onPress={() => onValueChange(item.key)} className={cn("min-h-touch min-w-touch flex-1 items-center justify-center gap-1", item.disabled && "opacity-50")}>
              <DesignAsset source={item.icon} contentFit="contain" tintColor={selected ? colors["icon-active"] : colors.icon} className="h-icon w-icon" accessible={false} />
              <Text numberOfLines={1} className={cn("text-micro text-foreground-secondary", selected && "text-label-sm text-primary")}>{item.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}
