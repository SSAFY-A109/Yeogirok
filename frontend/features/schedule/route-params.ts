type RouteParam = string | string[] | undefined;

function firstValue(value: RouteParam): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/** 초안 키로 받을 수 있는 글자와 길이. 서버가 만든 문자열(UUID)이며 서버가 다시 확인한다(SC001). */
const DRAFT_KEY_PATTERN = /^[A-Za-z0-9_-]{1,100}$/;

/** 일정 초안 화면(W14)이 보여 주는 일정. 저장된 일정에서 시작하거나("일정 조정하기") 만든 초안이다. */
export type ScheduleSource = { kind: "saved" } | { kind: "draft"; draftKey: string };

/** `draftKey` 파라미터. 없으면 저장된 일정에서 시작한다. 허용하지 않는 글자가 있으면 `null`. */
export function parseScheduleSource(draftKey: RouteParam): ScheduleSource | null {
  const raw = firstValue(draftKey);

  if (raw === undefined) {
    return { kind: "saved" };
  }

  return DRAFT_KEY_PATTERN.test(raw) ? { kind: "draft", draftKey: raw } : null;
}

/** 일정 조건 화면(W13)의 `keep` 파라미터 값. 저장된 일정의 날짜별 장소를 이어받는 초안을 만든다. */
export const KEEP_SAVED_PARAM = "saved";

export function parseKeepSaved(keep: RouteParam): boolean {
  return firstValue(keep) === KEEP_SAVED_PARAM;
}
