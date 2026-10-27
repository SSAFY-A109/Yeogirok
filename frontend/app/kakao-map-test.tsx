import { Redirect, Stack } from "expo-router";

import { KakaoMapTestScreen } from "@/features/explore/components/kakao-map-test-screen";

export { ErrorBoundary } from "expo-router";

export default function KakaoMapTestRoute() {
  if (!__DEV__) return <Redirect href="/" />;

  return (
    <>
      <Stack.Screen options={{ title: "지도 테스트" }} />
      <KakaoMapTestScreen />
    </>
  );
}
