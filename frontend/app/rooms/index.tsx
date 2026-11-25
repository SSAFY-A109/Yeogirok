import { Stack } from "expo-router";

import { RoomListScreen } from "@/features/room/components/room-list-screen";

export default function RoomsRoute() {
  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <RoomListScreen />
    </>
  );
}
