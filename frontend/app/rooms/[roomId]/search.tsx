import { Stack, useLocalSearchParams } from "expo-router";

import { SearchPlacesScreen } from "@/features/place/components/search-places-screen";
import { parseRoomId } from "@/features/room/route-params";

export default function SearchPlacesRoute() {
  const roomId = parseRoomId(useLocalSearchParams().roomId);

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <SearchPlacesScreen roomId={roomId} />
    </>
  );
}
