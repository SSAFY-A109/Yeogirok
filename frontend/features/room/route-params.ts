import { PLACE_PROVIDERS } from "@/features/course/model";
import type { PlaceTab } from "@/features/room/components/place-tabs";
import { toPlaceKey, type PlaceRefDto } from "@/features/room/model";

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

/** 초대 토큰으로 받을 수 있는 글자(URL에 그대로 실리는 base64url 계열)와 길이. 서버가 다시 검증한다(RO005). */
const INVITE_TOKEN_PATTERN = /^[A-Za-z0-9._~-]{1,512}$/;

/** 초대 링크의 토큰을 검증한다. 비었거나 허용하지 않는 글자가 있으면 `null`. */
export function parseInviteToken(value: RouteParam): string | null {
  const raw = firstValue(value);
  return raw !== undefined && INVITE_TOKEN_PATTERN.test(raw) ? raw : null;
}

/** 쉼표로 이은 id 목록(`101,102`)을 검증한다. 하나라도 숫자 id가 아니면 빈 배열이다. */
export function parseIds(value: RouteParam): number[] {
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

    ids.push(id);
  }

  return ids;
}

/** 장소 목록을 `/rooms/new`의 `placeRefs` 파라미터 문자열(`[["TOUR_API","112"]]`)로 만든다. `parsePlaceRefs`가 읽는 형식이다. */
export function formatPlaceRefsParam(placeRefs: readonly PlaceRefDto[]): string {
  return JSON.stringify(placeRefs.map(({ provider, providerPlaceId }) => {
    return [provider, providerPlaceId];
  }));
}

function toPlaceRef(value: unknown): PlaceRefDto | null {
  if (!Array.isArray(value) || value.length !== 2) {
    return null;
  }

  const [provider, providerPlaceId]: unknown[] = value;

  const isKnownProvider = PLACE_PROVIDERS.some((known) => {
    return known === provider;
  });

  if (typeof provider !== "string" || !isKnownProvider || typeof providerPlaceId !== "string" || providerPlaceId === "") {
    return null;
  }

  return { provider, providerPlaceId };
}

/**
 * 라우트 파라미터의 장소 목록(`formatPlaceRefsParam` 형식)을 검증한다. 같은 장소는 한 번만 남긴다.
 * 형식이 다르거나 하나라도 알려진 공급자·장소 식별값이 아니면 빈 배열이다.
 */
export function parsePlaceRefs(value: RouteParam): PlaceRefDto[] {
  const raw = firstValue(value);

  if (raw === undefined) {
    return [];
  }

  let parsed: unknown;

  try {
    parsed = JSON.parse(raw);
  } catch {
    return [];
  }

  if (!Array.isArray(parsed)) {
    return [];
  }

  const placeRefs = new Map<string, PlaceRefDto>();

  for (const item of parsed) {
    const placeRef = toPlaceRef(item);

    if (placeRef === null) {
      return [];
    }

    placeRefs.set(toPlaceKey(placeRef.provider, placeRef.providerPlaceId), placeRef);
  }

  return [...placeRefs.values()];
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
