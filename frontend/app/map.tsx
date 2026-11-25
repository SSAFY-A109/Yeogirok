import { Stack } from "expo-router";

import { VisitMapScreen } from "@/features/statistics/components/visit-map-screen";

export default function MapRoute() {
  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <VisitMapScreen />
    </>
  );
}
