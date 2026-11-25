import { listenForChatPushTokenChanges, prepareChatNotifications } from "../../lib/native/chat-notifications";
import { deletePushToken, registerPushToken } from "./api/user.api";

/** 알림 권한과 기기 토큰을 준비한 뒤, 받은 토큰을 서버(API-095)에 등록한다. 토큰이 없으면 등록하지 않는다. */
export async function registerChatPushToken(): Promise<void> {
  const token = await prepareChatNotifications();

  if (token) {
    await registerPushToken(token, "ANDROID");
  }
}

/** 기기 토큰이 바뀌면 새 토큰을 등록하고, 등록에 성공한 뒤 이전 토큰을 지운다(API-096). */
export function syncChatPushTokenChanges(onError: () => void) {
  return listenForChatPushTokenChanges((token, previous) => {
    void registerPushToken(token, "ANDROID")
      .then(() => {
        return previous ? deletePushToken(previous) : undefined;
      })
      .catch(onError);
  });
}
