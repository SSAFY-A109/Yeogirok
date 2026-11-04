import { Stack, useLocalSearchParams } from "expo-router";

import { RecommendScreen } from "@/features/recommendation/components/recommend-screen";
import { parseRoomId } from "@/features/room/route-params";

export default function RecommendRoute() {
  const roomId = parseRoomId(useLocalSearchParams().roomId);

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <RecommendScreen roomId={roomId} />
    </>
  );
}
