import { Stack, useLocalSearchParams } from "expo-router";

import { parseRoomId } from "@/features/room/route-params";
import { ScheduleDraftScreen } from "@/features/schedule/components/schedule-draft-screen";
import { parseDraftId } from "@/features/schedule/route-params";

export default function ScheduleDraftRoute() {
  const { roomId, draftId } = useLocalSearchParams<{ roomId?: string; draftId?: string }>();

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <ScheduleDraftScreen roomId={parseRoomId(roomId)} draftId={parseDraftId(draftId)} />
    </>
  );
}
