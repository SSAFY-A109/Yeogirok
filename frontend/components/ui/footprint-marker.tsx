import type { ComponentProps } from "react";
import { Pressable, View } from "react-native";

import { cn } from "@/lib/utils";
import { assets, DesignAsset } from "./design-asset";
import { Text } from "./text";

type FootprintMarkerProps = Omit<ComponentProps<typeof Pressable>, "children"> & {
  state?: "default" | "selected" | "cluster";
  count?: number;
};

export function FootprintMarker({ className, state = "default", count, accessibilityLabel, disabled, ...props }: FootprintMarkerProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? (state === "cluster" ? `발자취 ${count}개` : "발자취")}
      accessibilityState={{ selected: state === "selected", disabled }}
      disabled={disabled}
      className={cn("h-16 w-14 items-center", disabled && "opacity-50", className)}
      {...props}
    >
      <View className={cn("mt-1.5 h-input w-input items-center justify-center", state === "cluster" && "mt-px h-touch")}>
        {state === "selected" && <DesignAsset source={assets.footprintSelection} contentFit="contain" className="absolute h-input w-input" accessible={false} />}
        <DesignAsset source={assets.footprint} contentFit="contain" className="h-touch w-touch" accessible={false} />
      </View>
      {state === "cluster" && (
        <View className="absolute top-10 min-w-touch items-center justify-center rounded-full border-2 border-card bg-highlight-muted px-1 py-px">
          <Text className="text-label-sm tabular-nums text-highlight">{count}</Text>
        </View>
      )}
    </Pressable>
  );
}
