import { Link } from "expo-router";
import { Pressable, View } from "react-native";

import { Text } from "@/components/ui/text";

export default function Index() {
  return (
    <View className="flex-1 gap-5 bg-background px-5 py-5">
      <Text className="text-h1">여기록</Text>
      {__DEV__ && (
        <Link href="/kakao-map-test" asChild>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="카카오맵 테스트 화면 열기"
            className="min-h-touch items-center justify-center rounded-md bg-primary px-4 active:bg-primary-pressed"
            testID="home.kakao-map-test"
          >
            <Text className="text-button text-primary-foreground">카카오맵 테스트</Text>
          </Pressable>
        </Link>
      )}
    </View>
  );
}
