const pending = new Map<number, number[]>();

export async function getPendingTipIds(userId: number): Promise<number[]> {
  return pending.get(userId) ?? [];
}

export async function rememberPendingTip(userId: number, tipId: number): Promise<boolean> {
  pending.set(userId, [...new Set([...(pending.get(userId) ?? []), tipId])]);
  return true;
}

export async function forgetPendingTip(userId: number, tipId: number) {
  pending.set(userId, (pending.get(userId) ?? []).filter((id) => {
    return id !== tipId;
  }));
}
