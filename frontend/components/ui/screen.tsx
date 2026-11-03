import type { ReactNode } from "react";
import { View, type ViewProps } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { cn } from "@/lib/utils";

/** 화면 바깥 틀. 흰 배경과 상태 바 높이만큼의 위쪽 여백을 준다. 아래쪽 안전 영역은 하단 CTA·탭 바가 맡는다. */
export function Screen({ children, className, style, ...props }: ViewProps & { children: ReactNode }) {
  const insets = useSafeAreaInsets();

  return (
    <View className={cn("flex-1 bg-background", className)} style={[{ paddingTop: insets.top }, style]} {...props}>
      {children}
    </View>
  );
}
