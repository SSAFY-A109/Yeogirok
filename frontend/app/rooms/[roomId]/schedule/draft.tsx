import { Stack, useLocalSearchParams } from "expo-router";

import { parseRoomId } from "@/features/room/route-params";
import { ScheduleDraftScreen } from "@/features/schedule/components/schedule-draft-screen";
import { parseRecalculatedFrom, parseScheduleSource } from "@/features/schedule/route-params";

export default function ScheduleDraftRoute() {
  const { roomId, draftId, from } = useLocalSearchParams<{ roomId?: string; draftId?: string; from?: string }>();

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <ScheduleDraftScreen roomId={parseRoomId(roomId)} source={parseScheduleSource(draftId)} from={parseRecalculatedFrom(from)} />
    </>
  );
}
