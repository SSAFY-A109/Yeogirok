import { Stack, useLocalSearchParams } from "expo-router";

import { RoomDetailScreen } from "@/features/room/components/room-detail-screen";
import { parseRoomId } from "@/features/room/route-params";

export default function RoomDetailRoute() {
  const { roomId } = useLocalSearchParams<{ roomId?: string }>();

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <RoomDetailScreen roomId={parseRoomId(roomId)} />
    </>
  );
}
