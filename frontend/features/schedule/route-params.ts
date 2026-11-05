type RouteParam = string | string[] | undefined;

/** 라우트 파라미터의 일정 초안 id를 검증한다. 숫자 id가 아니면 `null`. */
export function parseDraftId(value: RouteParam): number | null {
  const raw = Array.isArray(value) ? value[0] : value;

  if (raw === undefined || !/^\d+$/.test(raw)) {
    return null;
  }

  const id = Number(raw);
  return Number.isSafeInteger(id) ? id : null;
}
