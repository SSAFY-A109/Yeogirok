export async function prepareChatNotifications(): Promise<string | null> {
  return null;
}

export function getChatPushToken(): string | null {
  return null;
}

export const listenForChatNotificationOpens: (onOpen: (roomId: number) => void) => (() => void) | undefined =
  () => {
    return undefined;
  };
