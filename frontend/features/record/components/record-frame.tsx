import { useFocusEffect } from "expo-router";
import { useCallback, type ReactNode } from "react";
import { BackHandler, ScrollView, View } from "react-native";

import { BottomCta } from "@/components/ui/bottom-cta";
import { Screen } from "@/components/ui/screen";
import { ScreenHeader } from "@/components/ui/screen-header";

export function RecordFrame({ title, onBack, trailing, banner, footer, children, centered = false, scroll = true }: {
  title: string; onBack: () => void; trailing?: ReactNode; banner?: ReactNode; footer?: ReactNode; children: ReactNode;
  centered?: boolean; scroll?: boolean;
}) {
  useFocusEffect(useCallback(() => {
    const back = BackHandler.addEventListener("hardwareBackPress", () => {
      onBack();
      return true;
    });

    return () => {
      return back.remove();
    };
  }, [onBack]));

  return <Screen className={banner ? "bg-accent" : undefined}>
    {!banner && <ScreenHeader title={title} titleAlign={centered ? "center" : "start"} onBack={onBack} trailing={trailing} />}
    {scroll ? <ScrollView keyboardShouldPersistTaps="handled" className="bg-background">
      {banner}
      <View className={banner ? "gap-4 px-5 pb-6 pt-5" : "gap-4 px-5 pb-6 pt-2"}>{children}</View>
    </ScrollView> : children}
    {footer && <BottomCta className="border-t border-border">{footer}</BottomCta>}
  </Screen>;
}
