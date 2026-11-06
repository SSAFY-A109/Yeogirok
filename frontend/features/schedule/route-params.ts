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

/** 일정 초안 화면(W14)이 보여 주는 일정. 여행방에 저장된 일정이거나 초안이다. */
export type ScheduleSource = { kind: "saved" } | { kind: "draft"; draftId: number };

const SAVED_PARAM = "saved";

/** `draftId` 파라미터. 없으면 저장된 일정을 고친다("일정 조정하기"). 숫자 id가 아니면 `null`. */
export function parseScheduleSource(draftId: RouteParam): ScheduleSource | null {
  if (draftId === undefined) {
    return { kind: "saved" };
  }

  const id = parseDraftId(draftId);
  return id === null ? null : { kind: "draft", draftId: id };
}

/** `from` 파라미터. 다시 계산하기 전에 보던 일정이다. 없거나 알 수 없는 값이면 `null`. */
export function parseRecalculatedFrom(from: RouteParam): ScheduleSource | null {
  const raw = Array.isArray(from) ? from[0] : from;

  if (raw === SAVED_PARAM) {
    return { kind: "saved" };
  }

  const id = parseDraftId(raw);
  return id === null ? null : { kind: "draft", draftId: id };
}

/** 일정을 라우트 파라미터로. 저장된 일정은 `draftId`를 비운다. */
export function toScheduleParams(source: ScheduleSource, from: ScheduleSource | null = null): { draftId: string | undefined; from: string | undefined } {
  return {
    draftId: source.kind === "draft" ? String(source.draftId) : undefined,
    from: from === null ? undefined : from.kind === "draft" ? String(from.draftId) : SAVED_PARAM,
  };
}
