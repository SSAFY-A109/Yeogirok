import { Stack, useLocalSearchParams } from "expo-router";

import { NewRoomScreen } from "@/features/room/components/new-room-screen";
import { parsePlaceIds, readRouteText } from "@/features/room/route-params";

export default function NewRoomRoute() {
  const params = useLocalSearchParams<{ placeIds?: string; placeNames?: string }>();
  const placeIds = parsePlaceIds(params.placeIds);
  const carryOver = placeIds.length > 0 ? { placeIds, placeNames: readRouteText(params.placeNames) } : null;

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <NewRoomScreen carryOver={carryOver} />
    </>
  );
}
