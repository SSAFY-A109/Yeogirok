import { Stack } from "expo-router";

import { NewRoomScreen } from "@/features/room/components/new-room-screen";

export default function NewRoomRoute() {
  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <NewRoomScreen />
    </>
  );
}
