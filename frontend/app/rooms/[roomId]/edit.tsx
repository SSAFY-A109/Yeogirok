import { Stack, useLocalSearchParams } from "expo-router";

import { EditRoomScreen } from "@/features/room/components/edit-room-screen";
import { parseRoomId } from "@/features/room/route-params";

export default function EditRoomRoute() {
  const roomId = parseRoomId(useLocalSearchParams().roomId);

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <EditRoomScreen roomId={roomId} />
    </>
  );
}
