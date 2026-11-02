import "../global.css";

import { Stack } from "expo-router";
import { PortalHost } from "@rn-primitives/portal";
import { View } from "react-native";
import { MainTabBar } from "@/components/main-tab-bar";
import { useFonts } from "expo-font";
import { Text } from "@/components/ui/text";

export default function RootLayout() {
  const [loaded, error] = useFonts({ "Noto Sans KR": require("@/native/ar/android/assets/NotoSansKR.ttf") });

  if (!loaded && !error) {
    return <View className="flex-1 items-center justify-center bg-background"><Text>여기록을 준비하고 있어요</Text></View>;
  }

  return (
    <View className="flex-1 bg-background">
      <View className="flex-1">
        <Stack>
          <Stack.Screen name="ar-tip" options={{ headerShown: false }} />
        </Stack>
      </View>
      <MainTabBar />
      <PortalHost />
    </View>
  );
}
