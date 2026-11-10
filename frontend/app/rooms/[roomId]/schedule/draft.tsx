import { Stack, useLocalSearchParams } from "expo-router";

import { parseRoomId } from "@/features/room/route-params";
import { ScheduleDraftScreen } from "@/features/schedule/components/schedule-draft-screen";
import { parseScheduleSource } from "@/features/schedule/route-params";

export default function ScheduleDraftRoute() {
  const { roomId, draftKey } = useLocalSearchParams<{ roomId?: string; draftKey?: string }>();

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <ScheduleDraftScreen roomId={parseRoomId(roomId)} source={parseScheduleSource(draftKey)} />
    </>
  );
}
