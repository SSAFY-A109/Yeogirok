import { Stack, useLocalSearchParams } from "expo-router";

import { parseRoomId } from "@/features/room/route-params";
import { ScheduleConditionsScreen } from "@/features/schedule/components/schedule-conditions-screen";

export default function ScheduleConditionsRoute() {
  const roomId = parseRoomId(useLocalSearchParams().roomId);

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <ScheduleConditionsScreen roomId={roomId} />
    </>
  );
}
