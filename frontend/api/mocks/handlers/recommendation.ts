import { delay, http } from "msw";

import { IDEMPOTENCY_HEADER } from "../../idempotency";
import type { PlaceRecommendationsDto, RecommendationPreferenceDto } from "../../../features/recommendation/model";
import { formatServerDateTime } from "../../../lib/date";

import { mockDb } from "../db";
import { findCategory } from "../fixtures/common";
import { findCatalogPlace } from "../fixtures/place";
import { recommendationSeeds } from "../fixtures/recommendation";
import { getActivePlaces } from "../fixtures/room";
import { toPlaceDto } from "./place";
import { fail, MOCK_DELAY_MS, ok, readId, roomNotFound } from "./respond";

/** AI 추천은 처리 중 화면(W10-1)을 확인할 수 있게 조금 더 기다린다. */
const RECOMMENDATION_DELAY_MS = 1_500;

/** `limit`을 보내지 않았을 때 주는 추천 수. 서버 기본값은 명세에 없다(TBD). */
const DEFAULT_RECOMMENDATION_COUNT = 3;

type JsonBody = Record<string, unknown>;

async function readBody(request: Request): Promise<JsonBody> {
  const body: unknown = await request.json().catch(() => {
    return null;
  });

  return typeof body === "object" && body !== null ? (body as JsonBody) : {};
}

function isCategoryCodes(value: unknown): value is string[] {
  return Array.isArray(value) && value.length > 0 && value.every((code) => {
    return typeof code === "string" && findCategory(code) !== null;
  });
}

/** `excludedPlaces`(PlaceRef[])에서 장소 식별값만 모은다. 모양이 다르면 `null`. */
function readExcludedPlaceIds(value: unknown): Set<string> | null {
  if (value === undefined) {
    return new Set();
  }

  if (!Array.isArray(value)) {
    return null;
  }

  const ids = new Set<string>();

  for (const item of value as { provider?: unknown; providerPlaceId?: unknown }[]) {
    if (typeof item?.provider !== "string" || typeof item.providerPlaceId !== "string") {
      return null;
    }

    ids.add(item.providerPlaceId);
  }

  return ids;
}

export const recommendationHandlers = [
  // 가정(TBD): API-081 상세를 확인하지 못했다. 저장한 조건이 없으면 빈 본문, 있으면 API-033 응답과 같은 모양으로 준다.
  http.get("*/api/v1/rooms/:roomId/preferences", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const roomId = readId(params.roomId);

    if (roomId === null || !mockDb.rooms.has(roomId)) {
      return roomNotFound();
    }

    return ok(mockDb.preferences.get(roomId) ?? null);
  }),

  // 가정(TBD): 요청의 version은 조건의 version으로 본다. 저장할 때마다 1 오르고, 저장된 값과 다르면 409 C004다.
  http.put("*/api/v1/rooms/:roomId/preferences", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const roomId = readId(params.roomId);

    if (roomId === null || !mockDb.rooms.has(roomId)) {
      return roomNotFound();
    }

    const { version, regionCode = null, categoryCodes, skipped } = await readBody(request);

    if (regionCode !== null && typeof regionCode !== "string") {
      return fail(400, "C003", "추천 조건이 올바르지 않습니다.");
    }

    if (typeof version !== "number" || !isCategoryCodes(categoryCodes) || skipped !== false) {
      return fail(400, "C003", "추천 조건이 올바르지 않습니다.");
    }

    const previous = mockDb.preferences.get(roomId);

    if (previous !== undefined && previous.version !== version) {
      return fail(409, "C004", "리소스 버전이 일치하지 않습니다.");
    }

    const response: RecommendationPreferenceDto = {
      roomId,
      preferences: { regionCode, categoryCodes, skipped: false },
      version: previous === undefined ? 1 : previous.version + 1,
    };

    mockDb.preferences.set(roomId, response);
    return ok(response);
  }),

  http.post("*/api/v1/rooms/:roomId/place-recommendations", async ({ params, request }) => {
    await delay(RECOMMENDATION_DELAY_MS);
    const roomId = readId(params.roomId);
    const room = roomId === null ? undefined : mockDb.rooms.get(roomId);

    if (roomId === null || room === undefined) {
      return roomNotFound();
    }

    if (!request.headers.get(IDEMPOTENCY_HEADER)?.trim()) {
      return fail(400, "C001", "Idempotency-Key가 필요합니다.");
    }

    const body = await readBody(request);
    const limit = body.limit ?? DEFAULT_RECOMMENDATION_COUNT;
    const excludedPlaceIds = readExcludedPlaceIds(body.excludedPlaces);

    if (typeof limit !== "number" || !Number.isInteger(limit) || limit < 1 || excludedPlaceIds === null) {
      return fail(400, "C003", "추천 요청이 올바르지 않습니다.");
    }

    // 추천은 저장된 조건으로 실행한다. 지역(regionCode)은 추천 실행 전 필수다. 실패 code는 명세에 없어 공통 code를 쓴다(TBD).
    if (!mockDb.preferences.get(roomId)?.preferences.regionCode) {
      return fail(400, "C003", "추천 조건의 지역이 필요합니다.");
    }

    const addedPlaceIds = new Set(getActivePlaces(room).map(({ place }) => {
      return place.providerPlaceId;
    }));

    const items = recommendationSeeds
      .filter((seed) => {
        return !addedPlaceIds.has(seed.providerPlaceId) && !excludedPlaceIds.has(seed.providerPlaceId);
      })
      .slice(0, limit)
      .flatMap((seed) => {
        const place = findCatalogPlace(seed.providerPlaceId);
        return place === null ? [] : [{ place: toPlaceDto(place), reason: seed.reason, reasonEvidence: seed.reasonEvidence }];
      })
      .map((item, index) => {
        return { rank: index + 1, ...item };
      });

    const response: PlaceRecommendationsDto = {
      recommendationRequestId: mockDb.nextRecommendationRequestId,
      mode: "PERSONALIZED",
      items,
      generatedAt: formatServerDateTime(new Date()),
    };

    mockDb.nextRecommendationRequestId += 1;
    return ok(response);
  }),
];
