import type { PlaceTab } from "@/features/room/components/place-tabs";

type RouteParam = string | string[] | undefined;

function firstValue(value: RouteParam): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function toNumericId(raw: string | undefined): number | null {
  if (raw === undefined || !/^\d+$/.test(raw)) {
    return null;
  }

  const id = Number(raw);
  return Number.isSafeInteger(id) ? id : null;
}

/** 라우트 파라미터의 여행방 id를 검증한다. 숫자 id가 아니면 `null`. */
export function parseRoomId(value: RouteParam): number | null {
  return toNumericId(firstValue(value));
}

/** 쉼표로 이은 장소 id 목록(`101,102`)을 검증한다. 하나라도 숫자 id가 아니면 빈 배열이다. */
export function parsePlaceIds(value: RouteParam): number[] {
  const raw = firstValue(value);

  if (raw === undefined || raw === "") {
    return [];
  }

  const ids: number[] = [];

  for (const part of raw.split(",")) {
    const id = toNumericId(part);

    if (id === null) {
      return [];
    }

    if (!ids.includes(id)) {
      ids.push(id);
    }
  }

  return ids;
}

/** 여행방 상세 탭. 모르는 값이면 장소 탭이다. */
export function parseRoomDetailTab(value: RouteParam): PlaceTab {
  return firstValue(value) === "schedule" ? "schedule" : "places";
}

/** 화면에 글자로만 보여 줄 라우트 파라미터. 비어 있으면 `null`. */
export function readRouteText(value: RouteParam): string | null {
  const text = firstValue(value)?.trim();
  return text ? text : null;
}
