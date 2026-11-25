import { Stack, useLocalSearchParams } from "expo-router";

import { InviteScreen } from "@/features/room/components/invite-screen";
import { parseRoomId } from "@/features/room/route-params";

export default function InviteRoute() {
  const roomId = parseRoomId(useLocalSearchParams().roomId);

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <InviteScreen roomId={roomId} />
    </>
  );
}
