import * as SecureStore from "expo-secure-store";

const key = (userId: number) => {
  return `yeogirok.tip.pending.${userId}`;
};

export async function getPendingTipIds(userId: number): Promise<number[]> {
  const raw = await SecureStore.getItemAsync(key(userId));

  if (raw === null) {
    return [];
  }

  const value: unknown = JSON.parse(raw);

  if (!Array.isArray(value) || !value.every((id) => {
    return typeof id === "number" && Number.isSafeInteger(id) && id > 0;
  })) {
    throw new Error("INVALID_PENDING_TIPS");
  }

  return value;
}

export async function rememberPendingTip(userId: number, tipId: number): Promise<boolean> {
  try {
    const ids = await getPendingTipIds(userId);
    await SecureStore.setItemAsync(key(userId), JSON.stringify([...new Set([...ids, tipId])]));
    return true;
  } catch {
    return false;
  }
}

export async function forgetPendingTip(userId: number, tipId: number) {
  const ids = await getPendingTipIds(userId);

  await SecureStore.setItemAsync(key(userId), JSON.stringify(ids.filter((id) => {
    return id !== tipId;
  })));
}
