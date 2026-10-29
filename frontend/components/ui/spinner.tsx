import { View, type ViewProps } from "react-native";

import { cn } from "@/lib/utils";
import { assets, DesignAsset } from "./design-asset";

type SpinnerProps = ViewProps & {
  tone?: "default" | "light" | "brand";
};

export function Spinner({ className, tone = "default", ...props }: SpinnerProps) {
  const source = { default: assets.spinner, light: assets.spinnerLight, brand: assets.spinnerBrand }[tone];

  return (
    <View accessibilityRole="progressbar" accessibilityLabel="불러오는 중" className={cn("size-4", className)} {...props}>
      <View className="size-4 animate-spin motion-reduce:animate-none" accessible={false}>
        <DesignAsset source={source} contentFit="contain" className="size-4" accessible={false} />
      </View>
    </View>
  );
}
