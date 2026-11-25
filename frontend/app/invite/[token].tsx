import { Redirect, useLocalSearchParams } from "expo-router";

import { useSession } from "@/features/auth/api/queries";
import { SocialLoginScreen } from "@/features/auth/components/social-login-screen";
import { JoinRoomScreen } from "@/features/room/components/join-room-screen";
import { parseInviteToken } from "@/features/room/route-params";

/** 여행방 초대 링크(`<초대 주소>/invite/{inviteToken}`)로 들어오는 참여 확인 화면. */
export default function JoinRoomRoute() {
  const session = useSession();
  const inviteToken = parseInviteToken(useLocalSearchParams().token);

  if (session.data !== true) {
    return inviteToken === null ? <Redirect href="/login" /> : <SocialLoginScreen />;
  }

  return <JoinRoomScreen key={inviteToken} inviteToken={inviteToken} />;
}
