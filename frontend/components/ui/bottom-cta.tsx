import type { ReactNode } from "react";
import { View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { cn } from "@/lib/utils";

/** 홈 인디케이터가 없는 기기에서도 버튼이 화면 끝에 붙지 않게 두는 최소 아래 여백. */
const MIN_BOTTOM_PADDING = 12;

/** 화면 하단에 고정하는 주요 버튼 영역(Figma `Bottom CTA`). 버튼 위 안내 줄 같은 요소도 함께 넣는다. */
export function BottomCta({ children, className }: { children: ReactNode; className?: string }) {
  const insets = useSafeAreaInsets();

  return (
    <View
      className={cn("items-center gap-3 bg-card px-5 pt-3", className)}
      style={{ paddingBottom: Math.max(insets.bottom, MIN_BOTTOM_PADDING) }}
    >
      {children}
    </View>
  );
}
