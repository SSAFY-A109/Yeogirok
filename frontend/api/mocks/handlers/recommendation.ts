import { delay, http } from "msw";

import type { RecommendationPreferenceDto } from "../../../features/recommendation/model";

import { mockDb } from "../db";
import { findCategory } from "../fixtures/common";
import { findCatalogPlace } from "../fixtures/place";
import { recommendationSeeds } from "../fixtures/recommendation";
import { MOCK_DELAY_MS, ok, readId, roomNotFound } from "./respond";

/** AI 추천은 처리 중 화면(W10-1)을 확인할 수 있게 조금 더 기다린다. */
const RECOMMENDATION_DELAY_MS = 1_500;
const RECOMMENDATION_COUNT = 3;

export const recommendationHandlers = [
  http.get("*/api/v1/rooms/:roomId/preferences", async ({ params }) => {
    await delay(MOCK_DELAY_MS);
    const roomId = readId(params.roomId);

    if (roomId === null || !mockDb.rooms.has(roomId)) {
      return roomNotFound();
    }

    return ok(mockDb.preferences.get(roomId) ?? null);
  }),

  http.put("*/api/v1/rooms/:roomId/preferences", async ({ params, request }) => {
    await delay(MOCK_DELAY_MS);
    const roomId = readId(params.roomId);

    if (roomId === null || !mockDb.rooms.has(roomId)) {
      return roomNotFound();
    }

    const body = (await request.json()) as RecommendationPreferenceDto;
    mockDb.preferences.set(roomId, { categoryCodes: body.categoryCodes, regionCode: body.regionCode });
    return ok(null);
  }),

  http.post("*/api/v1/rooms/:roomId/place-recommendations", async ({ params }) => {
    await delay(RECOMMENDATION_DELAY_MS);
    const roomId = readId(params.roomId);
    const room = roomId === null ? undefined : mockDb.rooms.get(roomId);

    if (roomId === null || room === undefined) {
      return roomNotFound();
    }

    const addedPlaceIds = new Set(room.places.map((place) => {
      return place.placeId;
    }));

    const items = recommendationSeeds
      .filter((seed) => {
        return !addedPlaceIds.has(seed.placeId);
      })
      .slice(0, RECOMMENDATION_COUNT)
      .flatMap((seed) => {
        const place = findCatalogPlace(seed.placeId);
        return place === null ? [] : [{ placeId: place.placeId, name: place.name, category: place.category, photoUrl: place.photoUrl, routeHint: seed.routeHint, reason: seed.reason }];
      });

    const criteria = (mockDb.preferences.get(roomId)?.categoryCodes ?? []).flatMap((code) => {
      const category = findCategory(code);
      return category === null ? [] : [category.name];
    });

    const requestId = mockDb.nextRecommendationRequestId;
    mockDb.nextRecommendationRequestId += 1;
    return ok({ requestId, criteria, items });
  }),
];
