import "../global.css";

import { useEffect, useState } from "react";
import { Stack } from "expo-router";
import { QueryClient, QueryClientProvider, useQueryClient } from "@tanstack/react-query";
import { PortalHost } from "@rn-primitives/portal";
import { View } from "react-native";
import { MainTabBar } from "@/components/main-tab-bar";
import { useFonts } from "expo-font";
import { Text } from "@/components/ui/text";
import { Button } from "@/components/ui/button";
import { setUnauthorizedHandler } from "@/api/client";
import { authKeys, useSession } from "@/features/auth/api/queries";

export default function RootLayout() {
  const [loaded, error] = useFonts({ "Noto Sans KR": require("@/native/ar/android/assets/NotoSansKR.ttf") });

  const [queryClient] = useState(() => {
    return new QueryClient();
  });

  if (!loaded && !error) {
    return <View className="flex-1 items-center justify-center bg-background"><Text>여기록을 준비하고 있어요</Text></View>;
  }

  return <QueryClientProvider client={queryClient}><RootNavigator /></QueryClientProvider>;
}

function RootNavigator() {
  const session = useSession();
  const queryClient = useQueryClient();

  useEffect(() => {
    setUnauthorizedHandler(() => {
      queryClient.setQueryData(authKeys.session, false);
      queryClient.removeQueries({
        predicate: (query) => {
          return query.queryKey[0] !== "auth" || query.queryKey[1] !== "session";
        },
      });
    });
  }, [queryClient]);

  if (session.isPending) {
    return <View className="flex-1 items-center justify-center bg-background"><Text>여기록을 준비하고 있어요</Text></View>;
  }

  if (session.isError) {
    return (
      <View className="flex-1 items-center justify-center gap-4 bg-background px-5">
        <Text className="text-center text-body">로그인 정보를 확인하지 못했어요. 다시 시도해 주세요.</Text>
        <Button onPress={() => {
          void session.refetch();
        }} accessibilityLabel="로그인 정보 다시 확인"><Text>다시 시도</Text></Button>
      </View>
    );
  }

  return (
    <View className="flex-1 bg-background">
      <View className="flex-1">
        <Stack>
          <Stack.Protected guard={session.data} redirectTo="/login">
            <Stack.Screen name="index" options={{ headerShown: false }} />
            <Stack.Screen name="courses/index" options={{ headerShown: false }} />
            <Stack.Screen name="courses/[courseId]" options={{ headerShown: false }} />
            <Stack.Screen name="ar-tip" options={{ headerShown: false }} />
            <Stack.Screen name="kakao-map-test" />
          </Stack.Protected>
          <Stack.Protected guard={!session.data} redirectTo="/">
            <Stack.Screen name="(auth)/login" options={{ headerShown: false }} />
          </Stack.Protected>
        </Stack>
      </View>
      {session.data && <MainTabBar />}
      <PortalHost />
    </View>
  );
}
