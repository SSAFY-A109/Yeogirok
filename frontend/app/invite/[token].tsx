import { Stack, useLocalSearchParams } from "expo-router";

import { JoinRoomScreen } from "@/features/room/components/join-room-screen";
import { parseInviteToken } from "@/features/room/route-params";

/** 여행방 초대 링크(`<초대 주소>/invite/{inviteToken}`)로 들어오는 참여 확인 화면. */
export default function JoinRoomRoute() {
  const inviteToken = parseInviteToken(useLocalSearchParams().token);

  return (
    <>
      <Stack.Screen options={{ headerShown: false }} />
      <JoinRoomScreen inviteToken={inviteToken} />
    </>
  );
}
