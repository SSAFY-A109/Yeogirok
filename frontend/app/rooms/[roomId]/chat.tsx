import { Stack, useLocalSearchParams } from "expo-router";

import { ChatScreen } from "@/features/room/components/chat-screen";
import { parseRoomId } from "@/features/room/route-params";

export default function ChatRoute() {
  const roomId = parseRoomId(useLocalSearchParams().roomId);

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <ChatScreen roomId={roomId} />
    </>
  );
}
