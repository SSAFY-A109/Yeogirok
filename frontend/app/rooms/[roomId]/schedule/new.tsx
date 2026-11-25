import { Stack, useLocalSearchParams } from "expo-router";

import { parseRoomId } from "@/features/room/route-params";
import { ScheduleConditionsScreen } from "@/features/schedule/components/schedule-conditions-screen";
import { parseKeepSaved } from "@/features/schedule/route-params";

export default function ScheduleConditionsRoute() {
  const { roomId, keep } = useLocalSearchParams<{ roomId?: string; keep?: string }>();

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <ScheduleConditionsScreen roomId={parseRoomId(roomId)} keepSaved={parseKeepSaved(keep)} />
    </>
  );
}
