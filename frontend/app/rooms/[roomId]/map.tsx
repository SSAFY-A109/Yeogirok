import { Stack, useLocalSearchParams } from "expo-router";

import { RoomMapScreen } from "@/features/room/components/room-map-screen";
import { parseRoomDetailTab, parseRoomId } from "@/features/room/route-params";

export default function RoomMapRoute() {
  const { roomId, view } = useLocalSearchParams<{ roomId?: string; view?: string }>();

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <RoomMapScreen roomId={parseRoomId(roomId)} view={parseRoomDetailTab(view)} />
    </>
  );
}
