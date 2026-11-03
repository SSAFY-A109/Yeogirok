/** 라우트 파라미터의 여행방 id를 검증한다. 숫자 id가 아니면 `null`. */
export function parseRoomId(value: string | string[] | undefined): number | null {
  const raw = Array.isArray(value) ? value[0] : value;

  if (raw === undefined || !/^\d+$/.test(raw)) {
    return null;
  }

  const id = Number(raw);
  return Number.isSafeInteger(id) ? id : null;
}
