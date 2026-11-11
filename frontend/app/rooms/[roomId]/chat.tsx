import { useLocalSearchParams } from "expo-router";

import { ChatScreen } from "@/features/room/components/chat-screen";
import { parseRoomId } from "@/features/room/route-params";

export default function ChatRoute() {
  const { roomId } = useLocalSearchParams<{ roomId?: string }>();
  return <ChatScreen key={roomId} roomId={parseRoomId(roomId)} />;
}
