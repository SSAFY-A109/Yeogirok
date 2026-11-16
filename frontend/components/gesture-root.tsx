import { cssInterop } from "nativewind";
import type { ReactNode } from "react";
import { GestureHandlerRootView } from "react-native-gesture-handler";

cssInterop(GestureHandlerRootView, { className: "style" });

/** 끌기 같은 제스처(react-native-gesture-handler)가 인식되도록 앱 전체를 감싼다. 루트 레이아웃에 한 번 둔다. */
export function GestureRoot({ children }: { children: ReactNode }) {
  return <GestureHandlerRootView className="flex-1">{children}</GestureHandlerRootView>;
}
