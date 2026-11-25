import { Pressable, View } from "react-native";

import { Text } from "@/components/ui/text";
import type { CurrentLocation } from "@/hooks/use-current-location";

/**
 * 대략적인 위치만 허용했을 때의 안내. 대략적인 위치는 수백 m 넘게 빗나갈 수 있어 정확한 위치로 바꾸게 한다.
 * 저장을 막지는 않는다. 위치 인정 여부는 서버가 판정한다(규칙 80).
 */
export function PreciseLocationNotice({ location, testID }: { location: CurrentLocation; testID: string }) {
  if (!location.isApproximate) {
    return null;
  }

  const opensSettings = location.preciseLocationAction === "settings";
  const actionLabel = opensSettings ? "설정 열기" : "정확한 위치 켜기";

  return (
    <View className="flex-row items-center gap-2 rounded-md bg-brand-muted pl-3" testID={testID}>
      <Text accessibilityRole="alert" className="flex-1 py-2 text-caption text-foreground">
        {opensSettings
          ? "대략적인 위치만 허용돼 있어요. 설정의 위치 권한에서 '정확한 위치 사용'을 켜 주세요."
          : "대략적인 위치만 허용돼 있어 위치가 크게 빗나갈 수 있어요."}
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={actionLabel}
        className="min-h-touch justify-center px-3"
        onPress={() => {
          void location.enablePreciseLocation();
        }}
        testID={`${testID}.enable`}
      >
        <Text className="text-label text-primary">{actionLabel}</Text>
      </Pressable>
    </View>
  );
}
