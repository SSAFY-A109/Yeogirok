import { Stack } from "expo-router";

import { NewFootprintScreen } from "@/features/footprint/components/new-footprint-screen";

export { ErrorBoundary } from "expo-router";

export default function NewFootprintRoute() {
  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <NewFootprintScreen />
    </>
  );
}
