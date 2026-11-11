import * as Notifications from "expo-notifications";
import { AndroidImportance } from "expo-notifications";

Notifications.setNotificationHandler({
  handleNotification: async () => {
    return {
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    };
  },
});

let pendingToken: Promise<string | null> | undefined;
let chatPushToken: string | null = null;

export function getChatPushToken(): string | null {
  return chatPushToken;
}

export function prepareChatNotifications(): Promise<string | null> {
  pendingToken ??= requestChatPushToken()
    .then((token) => {
      chatPushToken = token;
      return token;
    })
    .finally(() => {
      pendingToken = undefined;
    });

  return pendingToken;
}

async function requestChatPushToken(): Promise<string | null> {
  await Notifications.setNotificationChannelAsync("chat", {
    name: "채팅",
    importance: AndroidImportance.HIGH,
  });

  const current = await Notifications.getPermissionsAsync();
  const permission = !current.granted && current.canAskAgain
    ? await Notifications.requestPermissionsAsync()
    : current;

  if (!permission.granted) {
    return null;
  }

  const { data } = await Notifications.getDevicePushTokenAsync();
  return typeof data === "string" && data.length > 0 ? data : null;
}

function parseChatTarget(data: Record<string, unknown> | undefined) {
  if (data?.type !== "CHAT" || typeof data.roomId !== "string" || typeof data.messageId !== "string") {
    return null;
  }

  if (!/^[1-9]\d*$/.test(data.roomId) || !/^[1-9]\d*$/.test(data.messageId)) {
    return null;
  }

  const roomId = Number(data.roomId);
  return Number.isSafeInteger(roomId) ? { roomId, messageId: data.messageId } : null;
}

function getChatTarget(response: Notifications.NotificationResponse) {
  const { content, trigger } = response.notification.request;
  return parseChatTarget(content.data) ?? (
    trigger && "type" in trigger && trigger.type === "push" ? parseChatTarget(trigger.remoteMessage?.data) : null
  );
}

export function listenForChatNotificationOpens(onOpen: (roomId: number) => void) {
  let lastHandled: string | undefined;

  function handle(response: Notifications.NotificationResponse) {
    if (response.actionIdentifier !== Notifications.DEFAULT_ACTION_IDENTIFIER) {
      return;
    }

    const target = getChatTarget(response);

    if (!target) {
      return;
    }

    const pending = Notifications.getLastNotificationResponse();
    const pendingTarget = pending && getChatTarget(pending);

    if (pendingTarget?.roomId === target.roomId && pendingTarget.messageId === target.messageId) {
      Notifications.clearLastNotificationResponse();
    }

    const key = `${target.roomId}:${target.messageId}`;

    if (key !== lastHandled) {
      lastHandled = key;
      onOpen(target.roomId);
    }
  }

  const subscription = Notifications.addNotificationResponseReceivedListener(handle);
  const last = Notifications.getLastNotificationResponse();

  if (last) {
    handle(last);
  }

  return () => {
    return subscription.remove();
  };
}
