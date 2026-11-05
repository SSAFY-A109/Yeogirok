import { router, Stack, useLocalSearchParams } from "expo-router";

import { RoomDetailScreen } from "@/features/room/components/room-detail-screen";
import { parseRoomDetailTab, parseRoomId } from "@/features/room/route-params";

export default function RoomDetailRoute() {
  const { roomId, tab } = useLocalSearchParams<{ roomId?: string; tab?: string }>();

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <RoomDetailScreen
        roomId={parseRoomId(roomId)}
        tab={parseRoomDetailTab(tab)}

        onTabChange={(next) => {
          router.setParams({ tab: next });
        }}
      />
    </>
  );
}
